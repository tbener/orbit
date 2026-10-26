package com.smartdialer.rules

import kotlinx.coroutines.flow.Flow

/** Storage for the two separate rule lists, newest first. */
interface RulesRepository {
    val freeTextRules: Flow<List<FreeTextRule>>
    val typedRules: Flow<List<TypedRule>>

    /** The effects instructions were compiled into (see `ranking.Effects`), oldest first. */
    val effects: Flow<List<com.smartdialer.ranking.CompiledEffect>>

    /** Stores [params] as effects of the instruction [ruleId], enabled. */
    suspend fun addEffects(ruleId: Long, params: List<com.smartdialer.ranking.EffectParams>)
    suspend fun setEffectEnabled(id: Long, enabled: Boolean)
    suspend fun deleteEffect(id: Long)

    /** Removes every effect of [ruleId] (used by "Recompile" and when the instruction is edited or deleted). */
    suspend fun clearEffects(ruleId: Long)

    /**
     * Stores a new rule as [FreeTextStatus.UNCLASSIFIED]. Returns its id. A rule with a [contactKey]
     * is about that contact; without one it is a general instruction.
     */
    suspend fun addFreeTextRule(text: String, contactKey: String? = null): Long
    suspend fun updateFreeTextRule(id: Long, text: String)

    /**
     * Records how a rule was classified. [status] is [FreeTextStatus.SOFT], [FreeTextStatus.NEEDS_REVIEW] or
     * [FreeTextStatus.UNCLASSIFIED] (to send it back for classification). For a rule that has been converted,
     * delete the typed rule instead; that keeps the two lists consistent.
     */
    suspend fun setClassification(
        id: Long,
        status: FreeTextStatus,
        explanation: String?,
        proposedType: TypedRuleType? = null,
        proposedSlot: Int? = null,
    )

    /** Deleting a free-text rule keeps any typed rule it produced, but unlinks it. */
    suspend fun deleteFreeTextRule(id: Long)

    /**
     * Stores a typed rule and returns its id. When [sourceFreeTextRuleId] is given, that free-text
     * rule is marked [FreeTextStatus.CONVERTED_TO_TYPED] and linked to the new rule.
     * [createdAt] defaults to now; pass it to restore a deleted rule with its original age.
     * [explanation] becomes the source rule's explanation (a default is used when it is null).
     */
    suspend fun addTypedRule(
        params: TypedRuleParams,
        sourceFreeTextRuleId: Long? = null,
        createdAt: Long? = null,
        explanation: String? = null,
    ): Long

    suspend fun updateTypedRule(id: Long, params: TypedRuleParams)

    /**
     * Deleting a typed rule sets the free-text rule it came from back to [FreeTextStatus.SOFT] (so it is
     * not classified into the same hard rule again); the user can edit the text to have it reclassified.
     */
    suspend fun deleteTypedRule(id: Long)
}
