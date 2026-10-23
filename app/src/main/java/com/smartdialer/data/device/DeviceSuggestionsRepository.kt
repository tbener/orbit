package com.smartdialer.data.device

import com.smartdialer.data.CallLogSource
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactsSource
import com.smartdialer.data.DeviceContact
import com.smartdialer.data.FallbackRanker
import com.smartdialer.data.MAX_SUGGESTIONS
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Suggestions from the real contacts and call log, ranked by [FallbackRanker]. */
class DeviceSuggestionsRepository(
    private val contactsSource: ContactsSource,
    private val callLogSource: CallLogSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SuggestionsRepository {

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    override val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    override val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val mutex = Mutex()
    private var contacts: List<DeviceContact> = emptyList()

    override suspend fun refresh() = mutex.withLock {
        _isRefreshing.value = true
        try {
            val loaded = contactsSource.loadContacts()
            val now = nowMillis()
            val calls = callLogSource.loadCalls(now - HISTORY_WINDOW_MILLIS)
            contacts = loaded
            _suggestions.value = FallbackRanker.rank(loaded, calls, now).take(MAX_SUGGESTIONS)
        } finally {
            _isRefreshing.value = false
        }
    }

    override suspend fun searchContacts(query: String): List<Contact> {
        val text = query.trim()
        val digits = text.filter { it.isDigit() }
        val source = contacts.ifEmpty { contactsSource.loadContacts().also { contacts = it } }
        return source
            .filter { c ->
                c.contact.name.contains(text, ignoreCase = true) ||
                    (digits.length >= 3 && c.numbers.any { n -> n.filter { it.isDigit() }.contains(digits) })
            }
            .map { it.contact }
    }

    private companion object {
        const val HISTORY_WINDOW_MILLIS = 180L * 24 * 60 * 60 * 1000
    }
}
