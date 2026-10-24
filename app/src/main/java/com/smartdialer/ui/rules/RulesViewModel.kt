package com.smartdialer.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.llm.InstructionLimits
import com.smartdialer.ranking.TraceEntry
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
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
    /** Instructions kept but not sent to the LLM because of the per-request limit. */
    val unusedInstructions: Int = 0,
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
        RulesUiState(freeText, typed, deviceContacts?.map { it.contact }, enforced.trace, InstructionLimits.unusedCount(freeText))
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

    /** The user's override: this instruction becomes the given hard rule. */
    fun makeHard(freeTextId: Long, params: TypedRuleParams) {
        viewModelScope.launch {
            rules.addTypedRule(params, sourceFreeTextRuleId = freeTextId, explanation = "You made this a hard rule.")
        }
    }

    /** The user's override: remove the typed rule and keep the instruction as a soft one. */
    fun makeSoft(freeTextId: Long, typedRuleId: Long) {
        viewModelScope.launch {
            rules.deleteTypedRule(typedRuleId)
            rules.setClassification(freeTextId, FreeTextStatus.SOFT, "You made this a soft instruction.")
        }
    }

    fun keepSoft(freeTextId: Long) {
        viewModelScope.launch {
            rules.setClassification(freeTextId, FreeTextStatus.SOFT, "You kept this as a soft instruction.")
        }
    }
}
