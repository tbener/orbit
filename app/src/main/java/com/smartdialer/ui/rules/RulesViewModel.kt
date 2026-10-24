package com.smartdialer.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.ranking.TraceEntry
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val freeText: List<FreeTextRule> = emptyList(),
    val typed: List<TypedRule> = emptyList(),
    /** All device contacts, or null when they are unavailable (so "contact not found" cannot be judged). */
    val contacts: List<Contact>? = null,
    /** What the enforcement layer did with the typed rules the last time it ran. */
    val trace: List<TraceEntry> = emptyList(),
)

class RulesViewModel(
    private val rules: RulesRepository,
    contacts: ContactsRepository,
    pipeline: SuggestionPipeline,
) : ViewModel() {

    val state: StateFlow<RulesUiState> = combine(
        rules.freeTextRules,
        rules.typedRules,
        contacts.contacts,
        pipeline.result,
    ) { freeText, typed, deviceContacts, enforced ->
        RulesUiState(freeText, typed, deviceContacts?.map { it.contact }, enforced.trace)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    fun addFreeText(text: String) {
        viewModelScope.launch { rules.addFreeTextRule(text) }
    }

    fun updateFreeText(id: Long, text: String) {
        viewModelScope.launch { rules.updateFreeTextRule(id, text) }
    }

    fun deleteFreeText(id: Long) {
        viewModelScope.launch { rules.deleteFreeTextRule(id) }
    }

    fun addTyped(params: TypedRuleParams) {
        viewModelScope.launch { rules.addTypedRule(params) }
    }

    fun updateTyped(id: Long, params: TypedRuleParams) {
        viewModelScope.launch { rules.updateTypedRule(id, params) }
    }

    fun deleteTyped(id: Long) {
        viewModelScope.launch { rules.deleteTypedRule(id) }
    }
}
