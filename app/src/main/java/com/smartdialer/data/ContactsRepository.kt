package com.smartdialer.data

import kotlinx.coroutines.flow.StateFlow

/** The device contacts, shared by the home screen and the Rules page. */
interface ContactsRepository {
    /** All contacts that have a phone number, or null while they are unavailable (no permission, not loaded). */
    val contacts: StateFlow<List<DeviceContact>?>

    suspend fun reload()

    /** Searches all contacts by name or phone number. Never filtered by rules. */
    suspend fun search(query: String): List<Contact>
}
