package com.smartdialer.data.db

import androidx.room.withTransaction
import com.smartdialer.llm.CachedEntry
import com.smartdialer.llm.CachedRanking
import com.smartdialer.llm.EntrySource
import com.smartdialer.llm.LlmExchange
import com.smartdialer.llm.LlmExchangeStore
import com.smartdialer.llm.RankingCacheStore
import com.smartdialer.llm.Tier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun List<RankingCacheEntity>.toRanking(): CachedRanking? {
    val first = firstOrNull() ?: return null
    return CachedRanking(
        entries = map {
            CachedEntry(
                contactKey = it.contactKey,
                rank = it.rank,
                score = it.score,
                tier = Tier.parse(it.tier) ?: Tier.NORMAL,
                reason = it.reason,
                source = EntrySource.entries.firstOrNull { s -> s.name == it.source } ?: EntrySource.LLM,
                generatedAt = it.generatedAt,
            )
        },
        inputsHash = first.inputsHash,
        promptVersion = first.promptVersion,
        model = first.model,
        generatedAt = first.generatedAt,
    )
}

class RoomRankingCacheStore(private val db: OrbitDatabase) : RankingCacheStore {
    private val dao = db.rankingCacheDao()

    override val ranking: Flow<CachedRanking?> = dao.observe().map { it.toRanking() }

    override suspend fun load(): CachedRanking? = dao.load().toRanking()

    /** Replaces the whole cached ranking in one transaction, so readers never see a half-written one. */
    override suspend fun save(ranking: CachedRanking) = db.withTransaction {
        dao.clear()
        dao.insertAll(
            ranking.entries.map {
                RankingCacheEntity(
                    contactKey = it.contactKey,
                    rank = it.rank,
                    score = it.score,
                    tier = it.tier.name,
                    reason = it.reason,
                    source = it.source.name,
                    generatedAt = it.generatedAt,
                    inputsHash = ranking.inputsHash,
                    promptVersion = ranking.promptVersion,
                    model = ranking.model,
                )
            },
        )
    }
}

class RoomLlmExchangeStore(db: OrbitDatabase) : LlmExchangeStore {
    private val dao = db.llmExchangeDao()

    override fun observe(purpose: String): Flow<LlmExchange?> = dao.observe(purpose).map { row ->
        row?.let { LlmExchange(it.purpose, it.at, it.outcome, it.request, it.response, it.errors.lines().filter { l -> l.isNotBlank() }) }
    }

    override suspend fun save(exchange: LlmExchange) = dao.upsert(
        LlmExchangeEntity(
            purpose = exchange.purpose,
            at = exchange.at,
            outcome = exchange.outcome,
            request = exchange.request,
            response = exchange.response,
            errors = exchange.errors.joinToString("\n"),
        ),
    )
}
