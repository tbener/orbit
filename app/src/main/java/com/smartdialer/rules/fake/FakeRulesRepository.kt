package com.smartdialer.rules.fake

import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory rules for the stage 1 UI shell. Nothing is persisted. */
class FakeRulesRepository : RulesRepository {

    private val _freeText = MutableStateFlow(
        listOf(
            FreeTextRule("f1", "I want to talk to my mother every 2-3 days."),
            FreeTextRule("f2", "Surface people I usually call on a given weekday on that day."),
            FreeTextRule("f3", "Good friends I haven't spoken to in over a month matter more than acquaintances."),
        ),
    )
    private val _typed = MutableStateFlow<List<TypedRule>>(
        listOf(
            TypedRule.Pin("t1", "Mom", slot = 1),
            TypedRule.Hide("t2", "Gal (Work)"),
        ),
    )

    override val freeTextRules: StateFlow<List<FreeTextRule>> = _freeText.asStateFlow()
    override val typedRules: StateFlow<List<TypedRule>> = _typed.asStateFlow()

    override fun updateFreeTextRule(rule: FreeTextRule) =
        _freeText.update { list -> list.map { if (it.id == rule.id) rule else it } }

    override fun deleteFreeTextRule(id: String) =
        _freeText.update { list -> list.filterNot { it.id == id } }

    override fun updateTypedRule(rule: TypedRule) =
        _typed.update { list -> list.map { if (it.id == rule.id) rule else it } }

    override fun deleteTypedRule(id: String) =
        _typed.update { list -> list.filterNot { it.id == id } }
}
