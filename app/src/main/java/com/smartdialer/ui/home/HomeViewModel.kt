package com.smartdialer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.Contact
import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.ContactActivity
import com.smartdialer.data.LayoutMode
import com.smartdialer.data.ManualArrangement
import com.smartdialer.ranking.Reorder
import com.smartdialer.data.UiSettings
import com.smartdialer.llm.RefreshOutcome
import com.smartdialer.llm.SkipReason
import com.smartdialer.rules.ContactRef
import kotlinx.coroutines.flow.first
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.DeviceContact
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.NumberChooser
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.data.WhatsAppEvent
import com.smartdialer.data.WhatsAppEventStore
import com.smartdialer.data.WhatsAppSettings
import com.smartdialer.llm.AiSettings
import com.smartdialer.rules.RulesRepository
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.ui.UserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** The part of the ranked list currently shown; grows as the user scrolls. */
    val suggestions: List<Suggestion> = emptyList(),
    val selectedId: String? = null,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = false,
    val query: String = "",
    val searchResults: List<Contact> = emptyList(),
    /** The last AI refresh failed; the last good result is still shown. A small, non-blocking indicator. */
    val aiProblem: Boolean = false,
    /** The AI switch is on but the user has not yet read what is sent; nothing is sent until they accept. */
    val needsDisclosure: Boolean = false,
    /** Contacts under a never-show rule. Search still finds them; they are labelled. */
    val hiddenIds: Set<String> = emptySet(),
    /** AI suggestions are on (switch on and disclosure accepted). Off or pending means the sparkle shows as paused. */
    val aiOn: Boolean = true,
    /** What the open, empty search shows: frequent and recent contacts. */
    val searchSuggestions: SearchSuggestions = SearchSuggestions(),
    val layoutMode: LayoutMode = LayoutMode.Grid3,
    /** Contact id to the slot it is pinned to, for the pin marker. */
    val pinnedSlots: Map<String, Int> = emptyMap(),
    /** The small glass confirmation (pin, unpin, move) with its actions, shown for about six seconds. */
    val toast: Toast? = null,
    /** When the AI last ranked successfully (epoch millis), for the "Last calc" line. */
    val lastCalcAt: Long? = null,
    /**
     * A note for the card above the bottom bar. Nothing sets it yet; it is kept for later, for things like
     * "You wanted to call Aharon every 3 weeks, this is the time...". Null hides the card.
     */
    val notice: Notice? = null,
)

/** The card above the bottom bar: a short [title] and a [text]. */
class Notice(val title: String, val text: String)

/** A glass toast: [text], an optional primary action (such as "Pin") and an optional Undo. */
class Toast(
    val text: String,
    val primaryLabel: String? = null,
    val onPrimary: () -> Unit = {},
    val undo: (() -> Unit)? = null,
)

/** Up to eight contacts for the empty search, in two groups without duplicates. */
data class SearchSuggestions(val frequent: List<Contact> = emptyList(), val recent: List<Contact> = emptyList()) {
    val isEmpty: Boolean get() = frequent.isEmpty() && recent.isEmpty()
}

/** Builds [SearchSuggestions] from the call log facts. Pure. */
object SearchSuggestionsBuilder {
    fun build(activity: Map<String, ContactActivity>, contacts: List<DeviceContact>?): SearchSuggestions {
        if (contacts == null) return SearchSuggestions()
        val byKey = contacts.associate { it.contact.id to it.contact }
        val known = activity.filterKeys { it in byKey }
        val frequent = known.entries
            .filter { it.value.signals.callsLast90d > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, ContactActivity>> { it.value.signals.callsLast90d }
                .thenByDescending { it.value.signals.lastCallAt ?: 0L })
            .take(4).map { it.key }
        val recent = known.entries
            .filter { it.key !in frequent && it.value.signals.lastCallAt != null }
            .sortedByDescending { it.value.signals.lastCallAt }
            .take(8 - frequent.size).map { it.key }
        return SearchSuggestions(frequent.map(byKey::getValue), recent.map(byKey::getValue))
    }
}

class HomeViewModel(
    private val suggestions: SuggestionsRepository,
    pipeline: SuggestionPipeline,
    private val rules: RulesRepository,
    private val contacts: ContactsRepository,
    private val aiSettings: AiSettings,
    private val whatsAppStore: WhatsAppEventStore,
    private val whatsAppSettings: WhatsAppSettings,
    private val uiSettings: UiSettings,
    private val ai: AiSuggestionsRepository,
    private val manualArrangement: ManualArrangement,
) : ViewModel() {

    private data class Local(
        val selectedId: String? = null,
        val query: String = "",
        val searchResults: List<Contact> = emptyList(),
        val dragging: Boolean = false,
        val toast: Toast? = null,
    )

    private data class Extras(
        val ai: com.smartdialer.llm.AiSettingsState,
        val hidden: Set<String>,
        val searchSuggestions: SearchSuggestions,
        val layoutMode: LayoutMode,
    )

    private var frozenAll: List<Suggestion>? = null
    private var latestAll: List<Suggestion> = emptyList()
    private var latestVisible: List<Suggestion> = emptyList()
    private var latestPinned: Map<String, Int> = emptyMap()
    private var toastJob: Job? = null

    private val visibleCount = MutableStateFlow(PAGE_SIZE)
    private val local = MutableStateFlow(Local())
    private var searchJob: Job? = null
    private var accessGranted = false
    private var lastRefreshMillis = 0L

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages: Flow<UserMessage> = _messages.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        pipeline.result,
        suggestions.isRefreshing,
        visibleCount,
        local,
        combine(aiSettings.state, rules.typedRules, suggestions.activity, contacts.contacts, uiSettings.layoutMode) { ai, typed, activity, deviceContacts, layout ->
            Extras(
                ai,
                typed.map { it.params }.filterIsInstance<TypedRuleParams.NeverShow>().map { it.contact.lookupKey }.toSet(),
                SearchSuggestionsBuilder.build(activity, deviceContacts),
                layout,
            )
        },
    ) { enforced, refreshing, count, local, extras ->
        val (ai, hidden, searchSuggestions, layoutMode) = extras
        // While a bubble is being dragged, new results wait: the list is frozen and updated right after the drop.
        val all = if (local.dragging) (frozenAll ?: enforced.suggestions.also { frozenAll = it }) else enforced.suggestions.also { frozenAll = null }
        val visible = all.take(count)
        latestAll = all
        latestVisible = visible
        HomeUiState(
            suggestions = visible,
            selectedId = local.selectedId?.takeIf { id -> visible.any { it.contact.id == id } }
                ?: visible.firstOrNull()?.contact?.id,
            isRefreshing = refreshing,
            canLoadMore = count < all.size,
            query = local.query,
            searchResults = local.searchResults,
            aiProblem = ai.enabled && ai.disclosureAcknowledged && ai.lastFailureAt != null,
            needsDisclosure = ai.enabled && !ai.disclosureAcknowledged,
            hiddenIds = hidden,
            aiOn = ai.enabled && ai.disclosureAcknowledged,
            searchSuggestions = searchSuggestions,
            layoutMode = layoutMode,
            pinnedSlots = all.withIndex().filter { it.value.isPinned }.associate { it.value.contact.id to it.index + 1 }
                .also { latestPinned = it },
            toast = local.toast,
            lastCalcAt = ai.lastSuccessAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** The user accepted what is sent to the API; AI suggestions start (the auto-refresh notices). */
    fun acceptDisclosure() {
        aiSettings.acknowledgeDisclosure()
    }

    /** "Not now": AI suggestions are switched off, so nothing is ever sent. It can be turned on in Settings. */
    fun declineDisclosure() {
        aiSettings.setEnabled(false)
    }

    /** The WhatsApp button was used for [contact]: remember it as an outgoing event (when the feature is on). */
    fun onWhatsAppUsed(contact: Contact) {
        if (!whatsAppSettings.state.value.enabled) return
        viewModelScope.launch {
            whatsAppStore.record(WhatsAppEvent(contact.id, System.currentTimeMillis(), outgoing = true))
        }
    }

    /** Called when the contacts and call log permissions are granted or revoked. */
    fun onAccessChanged(granted: Boolean) {
        accessGranted = granted
        if (granted) refresh()
    }

    /** Called when the app returns to the foreground: reloads contacts and calls (cheap, no AI call). */
    fun onResumed() {
        if (accessGranted && System.currentTimeMillis() - lastRefreshMillis > STALE_AFTER_MILLIS) refresh()
    }

    /** Swipe down on the bubbles: ask the model again (counts against the daily cap), and say what happened. */
    fun pullRefresh() {
        viewModelScope.launch {
            val text = when (val outcome = ai.refreshManual()) {
                is RefreshOutcome.Skipped -> when (outcome.reason) {
                    SkipReason.DAILY_CAP -> "Daily AI limit reached, showing the last result"
                    SkipReason.AI_OFF, SkipReason.NOT_ACKNOWLEDGED -> "AI suggestions are off. Turn them on in Settings."
                    else -> null
                }
                is RefreshOutcome.Failed -> "Could not update suggestions, showing the last result"
                RefreshOutcome.Updated -> null
            }
            if (text != null) _messages.send(UserMessage(text))
        }
    }

    /**
     * Long-press on a bubble: pin the contact to the slot it is in now, or unpin it when it is already pinned.
     * Both show a confirmation with Undo. Pins and unpins are hard rules, so the list updates at once and no
     * LLM call is made.
     */
    fun togglePin(contact: Contact, slot: Int) {
        viewModelScope.launch {
            val typed = rules.typedRules.first()
            val existing = typed.firstOrNull { (it.params as? TypedRuleParams.PinToSlot)?.contact?.lookupKey == contact.id }
            if (existing != null) {
                rules.deleteTypedRule(existing.id)
                showToast(
                    Toast("${contact.name} unpinned", undo = {
                        viewModelScope.launch { rules.addTypedRule(existing.params, createdAt = existing.createdAt) }
                    }),
                )
                return@launch
            }
            val taken = typed.firstOrNull { (it.params as? TypedRuleParams.PinToSlot)?.slot == slot }
            if (taken != null) {
                val owner = (taken.params as TypedRuleParams.PinToSlot).contact.displayName
                showToast(Toast("Slot $slot is already pinned to $owner"))
                return@launch
            }
            val id = rules.addTypedRule(TypedRuleParams.PinToSlot(ContactRef(contact.id, contact.name), slot))
            showToast(Toast("${contact.name} pinned to #$slot", undo = { viewModelScope.launch { rules.deleteTypedRule(id) } }))
        }
    }

    /** True while a bubble is being dragged (and results are held back). */
    fun setDragging(active: Boolean) {
        local.update { it.copy(dragging = active) }
    }

    fun showToast(toast: Toast) {
        toastJob?.cancel()
        local.update { it.copy(toast = toast) }
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            local.update { it.copy(toast = null) }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        local.update { it.copy(toast = null) }
    }

    /**
     * A bubble was dropped on another slot (also used by the "Move to position" accessibility action). Slots are
     * 1-based rank positions in the displayed list. An unpinned bubble re-orders the list as a temporary manual
     * arrangement and offers to pin it there; a pinned bubble moves its pin. Moves are not sent to the LLM.
     */
    fun moveBubble(from: Int, to: Int) {
        val visibleIds = latestVisible.map { it.contact.id }
        when (val result = Reorder.move(visibleIds, latestPinned, from, to)) {
            Reorder.Result.NoOp, Reorder.Result.Blocked -> Unit
            is Reorder.Result.Moved -> {
                val name = latestVisible[from - 1].contact.name
                val previous = manualArrangement.order.value
                // The visible slots are rearranged; contacts further down keep their place after them.
                manualArrangement.set(result.order + latestAll.drop(visibleIds.size).map { it.contact.id })
                showToast(
                    Toast(
                        text = "Pin $name to slot $to?",
                        primaryLabel = "Pin",
                        onPrimary = { pinAt(latestVisible.getOrNull(to - 1)?.contact ?: return@Toast, to) },
                        undo = { manualArrangement.set(previous) },
                    ),
                )
            }
            is Reorder.Result.PinMoved -> viewModelScope.launch {
                val rule = rules.typedRules.first().firstOrNull {
                    (it.params as? TypedRuleParams.PinToSlot)?.contact?.lookupKey == result.contactKey
                } ?: return@launch
                val old = rule.params as TypedRuleParams.PinToSlot
                rules.updateTypedRule(rule.id, old.copy(slot = result.newSlot))
                showToast(
                    Toast(
                        "${old.contact.displayName} pinned to #${result.newSlot}",
                        undo = { viewModelScope.launch { rules.updateTypedRule(rule.id, old) } },
                    ),
                )
            }
        }
    }

    private fun pinAt(contact: Contact, slot: Int) {
        viewModelScope.launch {
            val typed = rules.typedRules.first()
            val taken = typed.any { (it.params as? TypedRuleParams.PinToSlot)?.slot == slot }
            if (taken) {
                showToast(Toast("Slot $slot is already pinned"))
                return@launch
            }
            val id = rules.addTypedRule(TypedRuleParams.PinToSlot(ContactRef(contact.id, contact.name), slot))
            showToast(Toast("${contact.name} pinned to #$slot", undo = { viewModelScope.launch { rules.deleteTypedRule(id) } }))
        }
    }

    private fun refresh() {
        lastRefreshMillis = System.currentTimeMillis()
        viewModelScope.launch { suggestions.refresh() }
    }

    fun loadMore() {
        visibleCount.update { it + PAGE_SIZE }
    }

    fun select(contactId: String) {
        local.update { it.copy(selectedId = contactId) }
    }

    fun onQueryChange(query: String) {
        local.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            local.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = contacts.search(query)
            local.update { it.copy(searchResults = results) }
        }
    }

    /** Picks the number to use for [contact]; tells the user when the contact has none. */
    fun chooseNumber(contact: Contact): NumberChoice {
        val numbers = contacts.contacts.value?.firstOrNull { it.contact.id == contact.id }?.numbers.orEmpty()
        val lastUsed = suggestions.activity.value[contact.id]?.lastUsedByNumberKey.orEmpty()
        val choice = NumberChooser.choose(numbers, lastUsed)
        if (choice == NumberChoice.None) {
            viewModelScope.launch { _messages.send(UserMessage("${contact.name} has no phone number")) }
        }
        return choice
    }

    /** Stores an instruction about one contact, unclassified. */
    fun addContactInstruction(contact: Contact, text: String) {
        viewModelScope.launch {
            val id = rules.addFreeTextRule(text, contactKey = contact.id)
            _messages.send(
                UserMessage("Instruction added for ${contact.name}") {
                    viewModelScope.launch { rules.deleteFreeTextRule(id) }
                },
            )
        }
    }

    /** Stores an instruction that applies to all contacts, unclassified. */
    fun addGeneralInstruction(text: String) {
        viewModelScope.launch {
            val id = rules.addFreeTextRule(text, contactKey = null)
            _messages.send(
                UserMessage("General instruction saved") {
                    viewModelScope.launch { rules.deleteFreeTextRule(id) }
                },
            )
        }
    }

    private companion object {
        // 12 fills whole rows in every layout (3 and 4 per row).
        const val PAGE_SIZE = 12
        const val SEARCH_DEBOUNCE_MS = 150L
        const val TOAST_MILLIS = 6_000L
        const val STALE_AFTER_MILLIS = 60_000L
    }
}
