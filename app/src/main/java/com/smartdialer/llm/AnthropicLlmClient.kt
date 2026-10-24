package com.smartdialer.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.random.Random

/** Builds the Messages API request body. Shared by the real and the fake client, so the debug view is honest. */
object AnthropicRequest {
    private val json = Json { encodeDefaults = true }

    fun body(request: LlmRequest): String {
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("system", request.system)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", request.user)
                        },
                    )
                },
            )
            // The 2026-10-21 API version rejects temperature / top_p / top_k, so none are sent.
            put(
                "output_config",
                buildJsonObject {
                    LlmConfig.EFFORT?.let { put("effort", it) }
                    put(
                        "format",
                        buildJsonObject {
                            put("type", "json_schema")
                            put("schema", request.schema)
                        },
                    )
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }
}

/** The real client: Anthropic Messages API over OkHttp, non-streaming, structured output. */
class AnthropicLlmClient(
    private val apiKey: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val endpoint: String = LlmConfig.ENDPOINT,
    private val maxAttempts: Int = LlmConfig.MAX_ATTEMPTS,
    /** Waits before a retry. Replaced in tests so they run instantly. */
    private val backoff: suspend (attempt: Int, retryAfterMillis: Long?) -> Unit = ::defaultBackoff,
) : LlmClient {

    private class Raw(val code: Int, val body: String, val retryAfterMillis: Long?)

    override suspend fun complete(request: LlmRequest): LlmResult {
        if (apiKey.isBlank()) {
            return LlmResult.Failure(FailureKind.NOT_CONFIGURED, "No API key is set (LLM_API_KEY in local.properties).", null)
        }
        val body = AnthropicRequest.body(request)
        var last: LlmResult.Failure? = null
        for (attempt in 1..maxAttempts) {
            val raw = try {
                send(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val kind = if (e is InterruptedIOException) FailureKind.TIMEOUT else FailureKind.NETWORK
                last = LlmResult.Failure(kind, e.message ?: e.javaClass.simpleName, body)
                if (attempt < maxAttempts) backoff(attempt, null)
                continue
            }
            val outcome = interpret(raw, body)
            if (outcome is LlmResult.Success) return outcome
            outcome as LlmResult.Failure
            last = outcome
            if (!isRetryable(outcome) || attempt == maxAttempts) return outcome
            backoff(attempt, raw.retryAfterMillis)
        }
        return last ?: LlmResult.Failure(FailureKind.NETWORK, "No attempt was made.", body)
    }

    private fun isRetryable(failure: LlmResult.Failure): Boolean = when (failure.kind) {
        FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.RATE_LIMITED, FailureKind.SERVER -> true
        else -> false
    }

    private fun interpret(raw: Raw, requestBody: String): LlmResult {
        val code = raw.code
        if (code == 200) return parseSuccess(raw.body, requestBody)
        val message = errorMessage(raw.body) ?: "HTTP $code"
        fun fail(kind: FailureKind) = LlmResult.Failure(kind, message, requestBody, raw.body, code)
        return when {
            code == 429 && raw.body.contains("enforced_spend_limit_reached") -> fail(FailureKind.SPEND_LIMIT)
            code == 429 -> fail(FailureKind.RATE_LIMITED)
            code == 400 && message.startsWith("You have reached your specified API usage limits") -> fail(FailureKind.SPEND_LIMIT)
            code == 401 || code == 402 || code == 403 -> fail(FailureKind.AUTH)
            code in 400..499 && code != 408 && code != 409 -> fail(FailureKind.BAD_REQUEST)
            else -> fail(FailureKind.SERVER)
        }
    }

    private fun parseSuccess(body: String, requestBody: String): LlmResult {
        fun fail(kind: FailureKind, message: String) = LlmResult.Failure(kind, message, requestBody, body, 200)
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return fail(FailureKind.MALFORMED, "The response was not JSON.")
        when (root["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> return fail(FailureKind.REFUSED, "The model declined to answer.")
            "max_tokens" -> return fail(FailureKind.TRUNCATED, "The answer was cut off (max_tokens).")
        }
        val content = root["content"] as? JsonArray ?: return fail(FailureKind.MALFORMED, "The response has no content.")
        val text = content
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: return fail(FailureKind.MALFORMED, "The response has no text block.")
        return LlmResult.Success(text, requestBody, body)
    }

    private fun errorMessage(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private suspend fun send(body: String): Raw = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey)
            .header("anthropic-version", LlmConfig.API_VERSION)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = try {
                        it.body?.string().orEmpty()
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                        return
                    }
                    val retryAfter = it.header("retry-after")?.toDoubleOrNull()?.let { s -> (s * 1000).toLong() }
                    if (continuation.isActive) continuation.resume(Raw(it.code, text, retryAfter))
                }
            }
        })
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(LlmConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(LlmConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(LlmConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Exponential backoff with jitter: about 1s, 2s, 4s; the server's `retry-after` wins when it is longer. */
        suspend fun defaultBackoff(attempt: Int, retryAfterMillis: Long?) {
            val exponential = 1_000L shl (attempt - 1).coerceAtMost(5)
            val wait = maxOf(exponential + Random.nextLong(0, 500), retryAfterMillis ?: 0L).coerceAtMost(60_000L)
            delay(wait)
        }
    }
}
