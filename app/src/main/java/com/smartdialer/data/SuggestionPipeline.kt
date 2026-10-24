package com.smartdialer.data

import com.smartdialer.ranking.EnforcementResult
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
) {
    val result: StateFlow<EnforcementResult> = combine(
        suggestions.suggestions,
        rules.typedRules,
        contacts.contacts,
    ) { ranked, typedRules, deviceContacts ->
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
