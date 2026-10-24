package com.smartdialer.llm

import kotlinx.serialization.json.JsonObject

/** One structured-output request: a system prompt, one user message, and the JSON schema the answer must follow. */
class LlmRequest(
    /** What the call is for ("ranking", "classification"); used for the debug record. */
    val purpose: String,
    val system: String,
    val user: String,
    val schema: JsonObject,
    val maxTokens: Int,
    val model: String = LlmConfig.MODEL,
)

enum class FailureKind {
    NOT_CONFIGURED, NETWORK, TIMEOUT, RATE_LIMITED, SPEND_LIMIT, AUTH, BAD_REQUEST, SERVER, REFUSED, TRUNCATED, MALFORMED,
}

sealed interface LlmResult {
    /** The exact request body that was sent, or null when nothing was sent. */
    val requestBody: String?
    val responseBody: String?

    /** [text] is the model's JSON answer. */
    class Success(val text: String, override val requestBody: String, override val responseBody: String) : LlmResult

    class Failure(
        val kind: FailureKind,
        val message: String,
        override val requestBody: String?,
        override val responseBody: String? = null,
        val httpStatus: Int? = null,
    ) : LlmResult
}

/**
 * Talks to a language model. Implementations never throw for network or API problems and never block the
 * caller's thread: every problem comes back as [LlmResult.Failure].
 */
interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmResult
}
