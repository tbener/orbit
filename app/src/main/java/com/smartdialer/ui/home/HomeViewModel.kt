package com.smartdialer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.NumberChoice
import com.smartdialer.data.NumberChooser
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.rules.RulesRepository
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
)

class HomeViewModel(
    private val suggestions: SuggestionsRepository,
    pipeline: SuggestionPipeline,
    private val rules: RulesRepository,
    private val contacts: ContactsRepository,
) : ViewModel() {

    private data class Local(
        val selectedId: String? = null,
        val query: String = "",
        val searchResults: List<Contact> = emptyList(),
    )

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
    ) { enforced, refreshing, count, local ->
        val all = enforced.suggestions
        val visible = all.take(count)
        HomeUiState(
            suggestions = visible,
            selectedId = local.selectedId?.takeIf { id -> visible.any { it.contact.id == id } }
                ?: visible.firstOrNull()?.contact?.id,
            isRefreshing = refreshing,
            canLoadMore = count < all.size,
            query = local.query,
            searchResults = local.searchResults,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Called when the contacts and call log permissions are granted or revoked. */
    fun onAccessChanged(granted: Boolean) {
        accessGranted = granted
        if (granted) refresh()
    }

    /** Called when the app returns to the foreground; refreshes if the data may be stale. */
    fun onResumed() {
        if (accessGranted && System.currentTimeMillis() - lastRefreshMillis > STALE_AFTER_MILLIS) refresh()
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
        // 7 = the hero cluster (3) plus one full row of 4 small bubbles.
        const val PAGE_SIZE = 7
        const val SEARCH_DEBOUNCE_MS = 150L
        const val STALE_AFTER_MILLIS = 60_000L
    }
}
