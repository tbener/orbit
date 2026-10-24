package com.smartdialer.rules

import kotlinx.coroutines.flow.Flow

/** Storage for the two separate rule lists, newest first. */
interface RulesRepository {
    val freeTextRules: Flow<List<FreeTextRule>>
    val typedRules: Flow<List<TypedRule>>

    /** Stores a new rule as [FreeTextStatus.UNCLASSIFIED]. Returns its id. */
    suspend fun addFreeTextRule(text: String): Long
    suspend fun updateFreeTextRule(id: Long, text: String)

    /** Deleting a free-text rule keeps any typed rule it produced, but unlinks it. */
    suspend fun deleteFreeTextRule(id: Long)

    /**
     * Stores a typed rule and returns its id. When [sourceFreeTextRuleId] is given, that free-text
     * rule is marked [FreeTextStatus.CONVERTED_TO_TYPED] and linked to the new rule.
     * [createdAt] defaults to now; pass it to restore a deleted rule with its original age.
     */
    suspend fun addTypedRule(
        params: TypedRuleParams,
        sourceFreeTextRuleId: Long? = null,
        createdAt: Long? = null,
    ): Long

    suspend fun updateTypedRule(id: Long, params: TypedRuleParams)

    /** Deleting a typed rule sets the free-text rule it came from back to [FreeTextStatus.UNCLASSIFIED]. */
    suspend fun deleteTypedRule(id: Long)
}
