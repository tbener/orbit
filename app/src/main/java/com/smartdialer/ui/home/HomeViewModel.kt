package com.smartdialer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.Contact
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val suggestions: List<Suggestion> = emptyList(),
    val selectedId: String? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val query: String = "",
    val searchResults: List<Contact> = emptyList(),
)

class HomeViewModel(private val repository: SuggestionsRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.isRefreshing.collect { refreshing ->
                _state.update { it.copy(isRefreshing = refreshing) }
            }
        }
        loadMore()
        viewModelScope.launch { repository.refresh() }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || !current.canLoadMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val page = repository.getSuggestions(offset = current.suggestions.size, limit = PAGE_SIZE)
            _state.update {
                it.copy(
                    suggestions = it.suggestions + page.items,
                    canLoadMore = page.hasMore,
                    isLoadingMore = false,
                    selectedId = it.selectedId ?: page.items.firstOrNull()?.contact?.id,
                )
            }
        }
    }

    fun select(contactId: String) {
        _state.update { it.copy(selectedId = contactId) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = repository.searchContacts(query)
            _state.update { it.copy(searchResults = results) }
        }
    }

    private companion object {
        // 7 = the hero cluster (3) plus one full row of 4 small bubbles.
        const val PAGE_SIZE = 7
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
