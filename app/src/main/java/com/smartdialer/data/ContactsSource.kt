package com.smartdialer.data

/** Reads contacts from the device. Returns an empty list when access is not granted. */
interface ContactsSource {
    suspend fun loadContacts(): List<DeviceContact>
}

/** Reads the device call log. Returns an empty list when access is not granted. */
interface CallLogSource {
    suspend fun loadCalls(sinceMillis: Long): List<CallRecord>
}
