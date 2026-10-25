package com.smartdialer.data

import com.smartdialer.ranking.EnforcementResult
import com.smartdialer.ranking.ManualOrder
import com.smartdialer.ranking.RuleEnforcer
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The home screen's list: the ranked suggestions, then the deterministic rule enforcement, always in
 * that order. Re-evaluated whenever the ranking, the typed rules or the contacts change.
 */
class SuggestionPipeline(
    suggestions: SuggestionsRepository,
    rules: RulesRepository,
    contacts: ContactsRepository,
    scope: CoroutineScope,
    manual: ManualArrangement = InMemoryManualArrangement(),
) {
    val result: StateFlow<EnforcementResult> = combine(
        suggestions.suggestions,
        rules.typedRules,
        contacts.contacts,
        manual.order,
    ) { rankedList, typedRules, deviceContacts, manualOrder ->
        // A temporary manual arrangement comes first; the hard rules still have the final word.
        val ranked = ManualOrder.apply(rankedList, manualOrder)
        if (deviceContacts == null) {
            // Contacts are unavailable, so rules cannot be resolved; show the plain ranking.
            EnforcementResult(ranked.take(MAX_SUGGESTIONS), emptyList())
        } else {
            RuleEnforcer.enforce(
                ranked,
                typedRules,
                deviceContacts.associate { it.contact.id to it.contact },
                MAX_SUGGESTIONS,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, EnforcementResult(emptyList(), emptyList()))
}
