package com.smartdialer.data.device

import com.smartdialer.data.CallLogSource
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.FallbackRanker
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Suggestions from the real contacts and call log, ranked by [FallbackRanker]. */
class DeviceSuggestionsRepository(
    private val contactsRepository: ContactsRepository,
    private val callLogSource: CallLogSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SuggestionsRepository {

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    override val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    override val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val mutex = Mutex()

    override suspend fun refresh() = mutex.withLock {
        _isRefreshing.value = true
        try {
            contactsRepository.reload()
            val contacts = contactsRepository.contacts.value.orEmpty()
            val now = nowMillis()
            val calls = callLogSource.loadCalls(now - HISTORY_WINDOW_MILLIS)
            _suggestions.value = FallbackRanker.rank(contacts, calls, now)
        } finally {
            _isRefreshing.value = false
        }
    }

    private companion object {
        const val HISTORY_WINDOW_MILLIS = 180L * 24 * 60 * 60 * 1000
    }
}
