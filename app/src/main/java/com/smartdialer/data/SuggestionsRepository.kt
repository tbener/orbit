package com.smartdialer.data

import kotlinx.coroutines.flow.StateFlow

/** The most suggestions the home screen will ever show. */
const val MAX_SUGGESTIONS = 20

/** Source of ranked suggestions, before any hard rules are applied. */
interface SuggestionsRepository {
    /** The full ranked list, best first, re-emitted whenever a refresh finishes. Not capped. */
    val suggestions: StateFlow<List<Suggestion>>

    /** Call-log derived facts per contact (by contact id), from the last refresh. */
    val activity: StateFlow<Map<String, ContactActivity>>

    /** True while a refresh of the ranking is running. */
    val isRefreshing: StateFlow<Boolean>

    /** Recomputes the ranking. Safe to call when permissions are missing; it then yields no suggestions. */
    suspend fun refresh()
}
