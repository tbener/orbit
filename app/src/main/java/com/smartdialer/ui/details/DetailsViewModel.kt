package com.smartdialer.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactSignals
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.data.WhatsAppEvent
import com.smartdialer.data.WhatsAppSignals
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.ui.UserMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailsUiState(
    val contactKey: String,
    /** The contact, or null when it is not in the device contacts (deleted, or contacts unavailable). */
    val contact: Contact? = null,
    /** True once the device contacts have been loaded, so a missing [contact] really means "not found". */
    val contactsLoaded: Boolean = false,
    /** Name to show when [contact] is missing, taken from a rule that mentions the contact. */
    val fallbackName: String = "Unknown contact",
    val signals: ContactSignals? = null,
    val instructions: List<FreeTextRule> = emptyList(),
    val typedRules: List<TypedRule> = emptyList(),
    /** The slot (1 = top) this contact has in the list right now, if they are in it. */
    val currentSlot: Int? = null,
    /** What the local engine computed for this contact: features, weights, contributions, applied effects. */
    val scored: com.smartdialer.ranking.ScoredContact? = null,
) {
    val displayName: String get() = contact?.name ?: fallbackName
}

class DetailsViewModel(
    private val contactKey: String,
    private val rules: RulesRepository,
    contacts: ContactsRepository,
    suggestions: SuggestionsRepository,
    whatsAppEvents: Flow<List<WhatsAppEvent>>,
    currentList: Flow<List<com.smartdialer.data.Suggestion>>,
    localRanking: com.smartdialer.data.LocalRanking,
) : ViewModel() {

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages: Flow<UserMessage> = _messages.receiveAsFlow()

    val state: StateFlow<DetailsUiState> = combine(
        rules.freeTextRules,
        rules.typedRules,
        contacts.contacts,
        combine(suggestions.activity, whatsAppEvents) { activity, events ->
            WhatsAppSignals.merge(activity, events, System.currentTimeMillis())
        },
        combine(currentList, localRanking.state) { list, local -> list to local },
    ) { freeText, typed, deviceContacts, activity, (list, local) ->
        val mine = typed.filter { it.params.contact.lookupKey == contactKey }
        DetailsUiState(
            contactKey = contactKey,
            contact = deviceContacts?.firstOrNull { it.contact.id == contactKey }?.contact,
            contactsLoaded = deviceContacts != null,
            fallbackName = mine.firstOrNull()?.params?.contact?.displayName ?: "Unknown contact",
            signals = activity[contactKey]?.signals,
            instructions = freeText.filter { it.contactKey == contactKey },
            typedRules = mine,
            scored = local?.ranked?.firstOrNull { it.contactKey == contactKey },
            currentSlot = list.indexOfFirst { it.contact.id == contactKey }.takeIf { it >= 0 }?.plus(1),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState(contactKey))

    fun addInstruction(text: String) {
        viewModelScope.launch { rules.addFreeTextRule(text, contactKey = contactKey) }
    }

    fun updateInstruction(id: Long, text: String) {
        viewModelScope.launch { rules.updateFreeTextRule(id, text) }
    }

    fun deleteInstruction(id: Long) {
        viewModelScope.launch { rules.deleteFreeTextRule(id) }
    }

    fun updateTyped(id: Long, params: TypedRuleParams) {
        viewModelScope.launch { rules.updateTypedRule(id, params) }
    }

    fun deleteTyped(id: Long) {
        viewModelScope.launch { rules.deleteTypedRule(id) }
    }

    /**
     * Adds a typed rule for this contact. A new pin replaces an existing pin for the same contact or the
     * same slot, so the newest choice wins (the enforcer itself always prefers the older rule). A
     * NEVER_SHOW rule is not added twice.
     */
    fun addTyped(params: TypedRuleParams) {
        viewModelScope.launch {
            val existing = rules.typedRules.first()
            when (params) {
                is TypedRuleParams.NeverShow -> {
                    val already = existing.any { it.params is TypedRuleParams.NeverShow && it.params.contact.lookupKey == contactKey }
                    if (already) {
                        _messages.send(UserMessage("Already hidden from suggestions"))
                    } else {
                        rules.addTypedRule(params)
                    }
                }
                is TypedRuleParams.PinToSlot -> {
                    val replaced = existing.filter {
                        val p = it.params
                        p is TypedRuleParams.PinToSlot && (p.contact.lookupKey == contactKey || p.slot == params.slot)
                    }
                    replaced.forEach { rules.deleteTypedRule(it.id) }
                    val newId = rules.addTypedRule(params)
                    val displaced = replaced.map { it.params.contact }.filter { it.lookupKey != contactKey }
                    if (displaced.isNotEmpty()) {
                        _messages.send(
                            UserMessage("Slot ${params.slot} was pinned for ${displaced.joinToString { it.displayName }}; that pin was replaced") {
                                viewModelScope.launch {
                                    rules.deleteTypedRule(newId)
                                    replaced.forEach { rules.addTypedRule(it.params, it.sourceFreeTextRuleId, it.createdAt) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
