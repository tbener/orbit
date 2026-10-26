package com.smartdialer.llm

import com.smartdialer.data.Contact
import com.smartdialer.data.ContactActivity
import com.smartdialer.data.ContactSignals
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.DeviceContact
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeRules : RulesRepository {
    private val free = MutableStateFlow<List<FreeTextRule>>(emptyList())
    private val typed = MutableStateFlow<List<TypedRule>>(emptyList())
    private var next = 1L
    override val freeTextRules: Flow<List<FreeTextRule>> = free.map { it.sortedByDescending { r -> r.id } }
    override val typedRules: Flow<List<TypedRule>> = typed
    private val effectList = MutableStateFlow<List<com.smartdialer.ranking.CompiledEffect>>(emptyList())
    override val effects: Flow<List<com.smartdialer.ranking.CompiledEffect>> = effectList

    override suspend fun addEffects(ruleId: Long, params: List<com.smartdialer.ranking.EffectParams>) {
        params.forEach { effectList.value += com.smartdialer.ranking.CompiledEffect(next++, ruleId, it) }
    }

    override suspend fun setEffectEnabled(id: Long, enabled: Boolean) {
        effectList.value = effectList.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun deleteEffect(id: Long) {
        effectList.value = effectList.value.filter { it.id != id }
    }

    override suspend fun clearEffects(ruleId: Long) {
        effectList.value = effectList.value.filter { it.sourceRuleId != ruleId }
    }

    override suspend fun addFreeTextRule(text: String, contactKey: String?): Long {
        val id = next++
        free.value += FreeTextRule(id, text, id, FreeTextStatus.UNCLASSIFIED, null, contactKey)
        return id
    }

    override suspend fun updateFreeTextRule(id: Long, text: String) = Unit

    override suspend fun setClassification(id: Long, status: FreeTextStatus, explanation: String?, proposedType: TypedRuleType?, proposedSlot: Int?) {
        free.value = free.value.map { if (it.id == id) it.copy(status = status, explanation = explanation, proposedType = proposedType, proposedSlot = proposedSlot) else it }
    }

    override suspend fun deleteFreeTextRule(id: Long) {
        free.value = free.value.filter { it.id != id }
    }

    override suspend fun addTypedRule(params: TypedRuleParams, sourceFreeTextRuleId: Long?, createdAt: Long?, explanation: String?): Long {
        val id = next++
        typed.value += TypedRule(id, createdAt ?: id, params, sourceFreeTextRuleId)
        if (sourceFreeTextRuleId != null) {
            free.value = free.value.map {
                if (it.id == sourceFreeTextRuleId) it.copy(status = FreeTextStatus.CONVERTED_TO_TYPED, typedRuleId = id, explanation = explanation ?: "Converted") else it
            }
        }
        return id
    }

    override suspend fun updateTypedRule(id: Long, params: TypedRuleParams) = Unit

    override suspend fun deleteTypedRule(id: Long) {
        typed.value = typed.value.filter { it.id != id }
        free.value = free.value.map { if (it.typedRuleId == id) it.copy(status = FreeTextStatus.SOFT, typedRuleId = null) else it }
    }
}

class FakeContacts(list: List<Contact>?) : ContactsRepository {
    private val state = MutableStateFlow(list?.map { DeviceContact(it, listOf("+1000")) })
    override val contacts: StateFlow<List<DeviceContact>?> = state.asStateFlow()
    override suspend fun reload() = Unit
    override suspend fun search(query: String): List<Contact> = emptyList()
}

class FakeLocal(
    list: List<Suggestion> = emptyList(),
    activityMap: Map<String, ContactActivity> = emptyMap(),
) : SuggestionsRepository {
    val list = MutableStateFlow(list)
    val activityState = MutableStateFlow(activityMap)
    override val suggestions: StateFlow<List<Suggestion>> = this.list
    override val activity: StateFlow<Map<String, ContactActivity>> = activityState
    override val isRefreshing: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun refresh() = Unit
}

fun activity(callCount: Int = 5, lastContact: Long? = 1_000_000L, missed: Int = 0) =
    ContactActivity(ContactSignals(lastContact, missed, if (missed > 0) lastContact else null, callCount), emptyMap())

fun rankingJson(vararg rows: Triple<String, Int, String>): String =
    """{"ranking":[${rows.joinToString(",") { """{"contactKey":"${it.first}","score":${it.second},"tier":"normal","reason":"${it.third}"}""" }}]}"""
