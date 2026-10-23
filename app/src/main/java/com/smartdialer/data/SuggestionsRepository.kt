package com.smartdialer.data

import kotlinx.coroutines.flow.StateFlow

/** Source of priority-ranked suggestions. Stage 1 uses a fake; real data replaces it later. */
interface SuggestionsRepository {
    /** True while a background refresh of the suggestions is running. */
    val isRefreshing: StateFlow<Boolean>

    /** Returns up to [limit] suggestions in priority order, starting at [offset]. */
    suspend fun getSuggestions(offset: Int, limit: Int): SuggestionPage

    /** Searches all contacts, not only the suggested ones. */
    suspend fun searchContacts(query: String): List<Contact>

    /** Refreshes the ranking in the background. */
    suspend fun refresh()
}
