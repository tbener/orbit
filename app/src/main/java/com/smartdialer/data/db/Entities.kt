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
@Entity(tableName = "free_text_rules", indices = [Index("typedRuleId")])
data class FreeTextRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val status: FreeTextStatus,
    val typedRuleId: Long?,
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

fun FreeTextRuleEntity.toDomain() = FreeTextRule(id, text, createdAt, status, typedRuleId)

/** Returns null for a row whose parameters cannot be read, instead of crashing the whole list. */
fun TypedRuleEntity.toDomainOrNull(): TypedRule? =
    runCatching { TypedRuleCodec.decode(paramsJson) }
        .getOrNull()
        ?.let { TypedRule(id, createdAt, it, sourceFreeTextRuleId) }

fun TypedRuleParams.toJson(): String = TypedRuleCodec.encode(this)
