package com.smartdialer.data.device

import com.smartdialer.data.Contact
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.ContactsSource
import com.smartdialer.data.DeviceContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceContactsRepository(private val source: ContactsSource) : ContactsRepository {

    private val _contacts = MutableStateFlow<List<DeviceContact>?>(null)
    override val contacts: StateFlow<List<DeviceContact>?> = _contacts.asStateFlow()

    override suspend fun reload() {
        _contacts.value = source.loadContacts()
    }

    override suspend fun search(query: String): List<Contact> {
        val text = query.trim()
        val digits = text.filter { it.isDigit() }
        val all = _contacts.value ?: source.loadContacts()?.also { _contacts.value = it } ?: return emptyList()
        return all
            .filter { c ->
                c.contact.name.contains(text, ignoreCase = true) ||
                    (digits.length >= 3 && c.numbers.any { n -> n.filter { it.isDigit() }.contains(digits) })
            }
            .map { it.contact }
    }
}
