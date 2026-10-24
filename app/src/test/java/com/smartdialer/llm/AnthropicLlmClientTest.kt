package com.smartdialer.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AnthropicLlmClientTest {
    private val request = LlmRequest("ranking", "sys", "user", buildJsonObject { put("type", "object") }, 100)
    private val ok = """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"a\":1}"}],"stop_reason":"end_turn"}"""

    private class Script(val replies: List<(okhttp3.Request) -> Response>) : Interceptor {
        var calls = 0
        val seen = mutableListOf<okhttp3.Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            seen += chain.request()
            return replies[minOf(calls++, replies.size - 1)](chain.request())
        }
    }

    private fun reply(code: Int, body: String, header: Pair<String, String>? = null): (okhttp3.Request) -> Response = { req ->
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("m")
            .apply { header?.let { addHeader(it.first, it.second) } }
            .body(body.toResponseBody()).build()
    }

    private fun client(key: String, script: Script) = AnthropicLlmClient(
        apiKey = key,
        http = OkHttpClient.Builder().addInterceptor(script).build(),
        backoff = { _, _ -> },
    )

    @Test
    fun successSendsTheRightRequestAndFindsTheTextBlock() = runTest {
        val s = Script(listOf(reply(200, ok)))
        val r = client("k", s).complete(request) as LlmResult.Success
        assertEquals("{\"a\":1}", r.text)
        assertEquals("k", s.seen.single().header("x-api-key"))
        assertEquals(LlmConfig.API_VERSION, s.seen.single().header("anthropic-version"))
        assertTrue(r.requestBody.contains("json_schema"))
        assertFalse(r.requestBody.contains("temperature"))
    }

    @Test
    fun retriesTransientErrorsThenSucceeds() = runTest {
        val s = Script(listOf(reply(529, "{}"), reply(429, "{}", "retry-after" to "1"), reply(200, ok)))
        assertTrue(client("k", s).complete(request) is LlmResult.Success)
        assertEquals(3, s.calls)
    }

    @Test
    fun doesNotRetryBadRequestsOrSpendLimit() = runTest {
        val bad = Script(listOf(reply(400, """{"error":{"message":"bad"}}""")))
        assertEquals(FailureKind.BAD_REQUEST, (client("k", bad).complete(request) as LlmResult.Failure).kind)
        assertEquals(1, bad.calls)
        val spend = Script(listOf(reply(429, """{"error":{"details":{"error_code":"enforced_spend_limit_reached"}}}""")))
        assertEquals(FailureKind.SPEND_LIMIT, (client("k", spend).complete(request) as LlmResult.Failure).kind)
        assertEquals(1, spend.calls)
    }

    @Test
    fun refusalTruncationAndNetworkFailuresAreFailuresNotCrashes() = runTest {
        val refusal = Script(listOf(reply(200, """{"content":[],"stop_reason":"refusal"}""")))
        assertEquals(FailureKind.REFUSED, (client("k", refusal).complete(request) as LlmResult.Failure).kind)
        val cut = Script(listOf(reply(200, """{"content":[],"stop_reason":"max_tokens"}""")))
        assertEquals(FailureKind.TRUNCATED, (client("k", cut).complete(request) as LlmResult.Failure).kind)
        val offline = Script(listOf({ _ -> throw IOException("down") }))
        assertEquals(FailureKind.NETWORK, (client("k", offline).complete(request) as LlmResult.Failure).kind)
        assertEquals(LlmConfig.MAX_ATTEMPTS, offline.calls)
    }

    @Test
    fun missingKeyNeverTouchesTheNetwork() = runTest {
        val s = Script(listOf(reply(200, ok)))
        assertEquals(FailureKind.NOT_CONFIGURED, (client("", s).complete(request) as LlmResult.Failure).kind)
        assertEquals(0, s.calls)
    }
}
