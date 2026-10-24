package com.smartdialer.llm

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/**
 * A scripted client with no network, for tests, previews and trying the app without a key. [respond] sees
 * each request and returns the result; every request is kept in [requests].
 */
class FakeLlmClient(
    private val latencyMillis: Long = 0,
    private val respond: (LlmRequest) -> LlmResult,
) : LlmClient {

    val requests = mutableListOf<LlmRequest>()

    override suspend fun complete(request: LlmRequest): LlmResult {
        requests += request
        if (latencyMillis > 0) delay(latencyMillis)
        return respond(request)
    }

    companion object {
        /** Answers every request with [text] as the model's JSON. */
        fun answering(text: String, latencyMillis: Long = 0) = FakeLlmClient(latencyMillis) { request ->
            LlmResult.Success(
                text,
                AnthropicRequest.body(request),
                """{"content":[{"type":"text","text":${JsonPrimitive(text)}}],"stop_reason":"end_turn"}""",
            )
        }

        fun failing(kind: FailureKind = FailureKind.NETWORK, message: String = "offline") = FakeLlmClient { request ->
            LlmResult.Failure(kind, message, AnthropicRequest.body(request))
        }
    }
}
