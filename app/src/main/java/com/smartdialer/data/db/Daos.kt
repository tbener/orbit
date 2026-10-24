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

    @Query("UPDATE free_text_rules SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM free_text_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE free_text_rules SET status = 'CONVERTED_TO_TYPED', typedRuleId = :typedRuleId WHERE id = :id")
    suspend fun markConverted(id: Long, typedRuleId: Long)

    /** Called when the typed rule [typedRuleId] is deleted: its source rule goes back to unclassified. */
    @Query("UPDATE free_text_rules SET status = 'UNCLASSIFIED', typedRuleId = NULL WHERE typedRuleId = :typedRuleId")
    suspend fun detachFromTyped(typedRuleId: Long)
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
