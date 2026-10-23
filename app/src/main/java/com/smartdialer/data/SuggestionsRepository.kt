package com.smartdialer.data

import kotlinx.coroutines.flow.StateFlow

/** The most suggestions the home screen will ever show. */
const val MAX_SUGGESTIONS = 20

/** Source of priority-ranked suggestions. */
interface SuggestionsRepository {
    /** The full ranked list (at most [MAX_SUGGESTIONS]), re-emitted whenever a refresh finishes. */
    val suggestions: StateFlow<List<Suggestion>>

    /** True while a refresh of the suggestions is running. */
    val isRefreshing: StateFlow<Boolean>

    /** Searches all contacts, not only the suggested ones. */
    suspend fun searchContacts(query: String): List<Contact>

    /** Recomputes the ranking. Safe to call when permissions are missing; it then yields no suggestions. */
    suspend fun refresh()
}
