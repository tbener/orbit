package com.smartdialer.rules

import kotlinx.coroutines.flow.StateFlow

/** Storage for the two separate rule lists. */
interface RulesRepository {
    val freeTextRules: StateFlow<List<FreeTextRule>>
    val typedRules: StateFlow<List<TypedRule>>

    fun updateFreeTextRule(rule: FreeTextRule)
    fun deleteFreeTextRule(id: String)

    fun updateTypedRule(rule: TypedRule)
    fun deleteTypedRule(id: String)
}
