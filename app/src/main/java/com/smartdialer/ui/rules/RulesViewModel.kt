package com.smartdialer.ui.rules

import androidx.lifecycle.ViewModel
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import kotlinx.coroutines.flow.StateFlow

class RulesViewModel(private val repository: RulesRepository) : ViewModel() {
    val freeTextRules: StateFlow<List<FreeTextRule>> = repository.freeTextRules
    val typedRules: StateFlow<List<TypedRule>> = repository.typedRules

    fun updateFreeText(rule: FreeTextRule) = repository.updateFreeTextRule(rule)
    fun deleteFreeText(id: String) = repository.deleteFreeTextRule(id)
    fun updateTyped(rule: TypedRule) = repository.updateTypedRule(rule)
    fun deleteTyped(id: String) = repository.deleteTypedRule(id)
}
