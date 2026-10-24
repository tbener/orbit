package com.smartdialer.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Storage for the latest ranking. One ranking at a time; saving replaces the previous one as a whole. */
interface RankingCacheStore {
    val ranking: Flow<CachedRanking?>
    suspend fun load(): CachedRanking?
    suspend fun save(ranking: CachedRanking)
}

/** The last request and answer of one kind of LLM call, kept so the user can see what was really sent. */
data class LlmExchange(
    val purpose: String,
    val at: Long,
    /** A short summary of how the call went. */
    val outcome: String,
    /** The exact request body that was sent, or null when nothing was sent. */
    val request: String?,
    /** The raw response body. */
    val response: String?,
    /** Validation problems and errors, one per entry. */
    val errors: List<String>,
)

interface LlmExchangeStore {
    fun observe(purpose: String): Flow<LlmExchange?>
    suspend fun save(exchange: LlmExchange)
}

class InMemoryRankingCacheStore(initial: CachedRanking? = null) : RankingCacheStore {
    private val state = MutableStateFlow(initial)
    override val ranking: Flow<CachedRanking?> = state.asStateFlow()
    override suspend fun load(): CachedRanking? = state.value
    override suspend fun save(ranking: CachedRanking) {
        state.value = ranking
    }
}

class InMemoryLlmExchangeStore : LlmExchangeStore {
    private val state = MutableStateFlow<Map<String, LlmExchange>>(emptyMap())
    override fun observe(purpose: String): Flow<LlmExchange?> = state.map { it[purpose] }
    override suspend fun save(exchange: LlmExchange) {
        state.update { it + (exchange.purpose to exchange) }
    }
}

/** A plain-language line for each way a call can fail, for the Settings screen. */
fun FailureKind.describe(): String = when (this) {
    FailureKind.NOT_CONFIGURED -> "No API key is set."
    FailureKind.NETWORK -> "Could not reach the service."
    FailureKind.TIMEOUT -> "The service took too long to answer."
    FailureKind.RATE_LIMITED -> "The service is busy (rate limit)."
    FailureKind.SPEND_LIMIT -> "The API usage limit was reached."
    FailureKind.AUTH -> "The API key was rejected."
    FailureKind.BAD_REQUEST -> "The service rejected the request."
    FailureKind.SERVER -> "The service had a problem."
    FailureKind.REFUSED -> "The model declined to answer."
    FailureKind.TRUNCATED -> "The answer was cut off."
    FailureKind.MALFORMED -> "The answer could not be read."
}
