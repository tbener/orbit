package com.smartdialer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleCodec
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType

/**
 * The two rule lists live in separate tables. The links between them (`typedRuleId` and
 * `sourceFreeTextRuleId`) are kept consistent by [RoomRulesRepository], inside transactions.
 */
@Entity(tableName = "free_text_rules", indices = [Index("typedRuleId"), Index("contactKey")])
data class FreeTextRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val status: FreeTextStatus,
    val typedRuleId: Long?,
    /** Lookup key of the contact this rule is about; null for a general instruction. */
    val contactKey: String? = null,
    val explanation: String? = null,
    val proposedType: TypedRuleType? = null,
    val proposedSlot: Int? = null,
)

/** [paramsJson] holds the type-specific parameters, so new rule types need no schema change. */
@Entity(tableName = "typed_rules", indices = [Index("sourceFreeTextRuleId")])
data class TypedRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val type: TypedRuleType,
    val paramsJson: String,
    val sourceFreeTextRuleId: Long?,
)

fun FreeTextRuleEntity.toDomain() =
    FreeTextRule(id, text, createdAt, status, typedRuleId, contactKey, explanation, proposedType, proposedSlot)

/** Returns null for a row whose parameters cannot be read, instead of crashing the whole list. */
fun TypedRuleEntity.toDomainOrNull(): TypedRule? =
    runCatching { TypedRuleCodec.decode(paramsJson) }
        .getOrNull()
        ?.let { TypedRule(id, createdAt, it, sourceFreeTextRuleId) }

fun TypedRuleParams.toJson(): String = TypedRuleCodec.encode(this)

/**
 * The latest ranking, one row per contact. The inputs hash, prompt version and model are repeated on every
 * row so the whole ranking and what it was made from are always read together, in one query.
 */
@Entity(tableName = "ranking_cache")
data class RankingCacheEntity(
    @PrimaryKey val contactKey: String,
    val rank: Int,
    val score: Int,
    val tier: String,
    val reason: String,
    val source: String,
    val generatedAt: Long,
    val inputsHash: String,
    val promptVersion: String,
    val model: String,
)

/** The last request and answer of each kind of LLM call, for the "What the LLM sees" screen. */
@Entity(tableName = "llm_exchanges")
data class LlmExchangeEntity(
    @PrimaryKey val purpose: String,
    val at: Long,
    val outcome: String,
    val request: String?,
    val response: String?,
    /** Validation issues and errors, one per line. */
    val errors: String,
)
