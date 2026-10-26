package com.smartdialer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FreeTextRuleDao {
    @Query("SELECT * FROM free_text_rules ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FreeTextRuleEntity>>

    @Query("SELECT * FROM free_text_rules WHERE id = :id")
    suspend fun get(id: Long): FreeTextRuleEntity?

    @Insert
    suspend fun insert(rule: FreeTextRuleEntity): Long

    /** Editing a soft or unclear rule sends it back for classification; a converted rule keeps its typed rule. */
    @Query(
        "UPDATE free_text_rules SET text = :text, " +
            "status = CASE WHEN status IN ('SOFT', 'NEEDS_REVIEW', 'REJECTED_OFF_TOPIC', 'COMPILED', 'UNSUPPORTED') THEN 'UNCLASSIFIED' ELSE status END, " +
            "explanation = CASE WHEN status IN ('SOFT', 'NEEDS_REVIEW', 'REJECTED_OFF_TOPIC', 'COMPILED', 'UNSUPPORTED') THEN NULL ELSE explanation END, " +
            "proposedType = NULL, proposedSlot = NULL WHERE id = :id",
    )
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE free_text_rules SET status = :status, explanation = :explanation, proposedType = :proposedType, proposedSlot = :proposedSlot WHERE id = :id")
    suspend fun setClassification(
        id: Long,
        status: com.smartdialer.rules.FreeTextStatus,
        explanation: String?,
        proposedType: com.smartdialer.rules.TypedRuleType?,
        proposedSlot: Int?,
    )

    @Query("DELETE FROM free_text_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE free_text_rules SET status = 'CONVERTED_TO_TYPED', typedRuleId = :typedRuleId, explanation = :explanation, " +
            "proposedType = NULL, proposedSlot = NULL WHERE id = :id",
    )
    suspend fun markConverted(id: Long, typedRuleId: Long, explanation: String)

    /** Called when the typed rule [typedRuleId] is deleted: its source rule goes back to being a soft instruction. */
    @Query(
        "UPDATE free_text_rules SET status = 'SOFT', typedRuleId = NULL, explanation = :explanation " +
            "WHERE typedRuleId = :typedRuleId",
    )
    suspend fun detachFromTyped(typedRuleId: Long, explanation: String)
}

@Dao
interface TypedRuleDao {
    @Query("SELECT * FROM typed_rules ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<TypedRuleEntity>>

    @Query("SELECT * FROM typed_rules WHERE id = :id")
    suspend fun get(id: Long): TypedRuleEntity?

    @Insert
    suspend fun insert(rule: TypedRuleEntity): Long

    @Query("UPDATE typed_rules SET type = :type, paramsJson = :paramsJson WHERE id = :id")
    suspend fun updateParams(id: Long, type: com.smartdialer.rules.TypedRuleType, paramsJson: String)

    @Query("DELETE FROM typed_rules WHERE id = :id")
    suspend fun delete(id: Long)

    /** Called when the free-text rule [freeTextRuleId] is deleted: typed rules it produced are kept, unlinked. */
    @Query("UPDATE typed_rules SET sourceFreeTextRuleId = NULL WHERE sourceFreeTextRuleId = :freeTextRuleId")
    suspend fun clearSource(freeTextRuleId: Long)
}

@Dao
interface RankingCacheDao {
    @Query("SELECT * FROM ranking_cache ORDER BY rank")
    fun observe(): Flow<List<RankingCacheEntity>>

    @Query("SELECT * FROM ranking_cache ORDER BY rank")
    suspend fun load(): List<RankingCacheEntity>

    @Query("DELETE FROM ranking_cache")
    suspend fun clear()

    @Insert
    suspend fun insertAll(rows: List<RankingCacheEntity>)
}

@Dao
interface LlmExchangeDao {
    @Query("SELECT * FROM llm_exchanges WHERE purpose = :purpose")
    fun observe(purpose: String): Flow<LlmExchangeEntity?>

    @androidx.room.Upsert
    suspend fun upsert(exchange: LlmExchangeEntity)
}

@Dao
interface CompiledEffectDao {
    @Query("SELECT * FROM compiled_effects ORDER BY createdAt, id")
    fun observeAll(): Flow<List<CompiledEffectEntity>>

    @Insert
    suspend fun insert(effect: CompiledEffectEntity): Long

    @Query("UPDATE compiled_effects SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM compiled_effects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM compiled_effects WHERE sourceRuleId = :ruleId")
    suspend fun deleteForRule(ruleId: Long)
}

@Dao
interface WhatsAppEventDao {
    @Query("SELECT * FROM whatsapp_events ORDER BY at DESC")
    fun observeAll(): Flow<List<WhatsAppEventEntity>>

    @Query("SELECT * FROM whatsapp_events ORDER BY at DESC")
    suspend fun all(): List<WhatsAppEventEntity>

    @Insert
    suspend fun insert(event: WhatsAppEventEntity)

    @Query("DELETE FROM whatsapp_events WHERE at < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM whatsapp_events")
    suspend fun deleteAll()
}
