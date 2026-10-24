package com.smartdialer.llm

/** Every tunable of the LLM integration in one place. */
object LlmConfig {
    /**
     * The model used for every call. To switch to a smaller, faster model change this one line, for example
     * to `"claude-haiku-5-5"` (and set [EFFORT] to null if that model rejects `output_config.effort`).
     */
    const val MODEL = "claude-sonnet-5-5"

    const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    const val API_VERSION = "2026-10-21"

    /** `output_config.effort` (low, medium, high, ...). Null leaves it out. The model's own thinking stays adaptive. */
    val EFFORT: String? = "medium"

    const val RANKING_MAX_TOKENS = 16_000
    const val CLASSIFY_MAX_TOKENS = 4_000

    const val CONNECT_TIMEOUT_SECONDS = 20L

    /** One non-streaming call may take a while for a long ranking. */
    const val CALL_TIMEOUT_SECONDS = 170L
    const val MAX_ATTEMPTS = 3

    /** A ranking older than this is refreshed when the app opens. */
    const val STALE_AFTER_MILLIS = 3L * 60 * 60 * 1000

    /** After a failed refresh, wait this long before trying again on open. */
    const val RETRY_AFTER_FAILURE_MILLIS = 15L * 60 * 1000

    /** Background refresh period. */
    const val PERIODIC_REFRESH_HOURS = 6L

    const val MAX_CANDIDATES = 100
    const val HISTORY_DAYS = 180
    const val MAX_REASON_LENGTH = 200
}
