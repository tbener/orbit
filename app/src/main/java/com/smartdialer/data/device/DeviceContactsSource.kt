package com.smartdialer.data.device

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.smartdialer.data.Contact
import com.smartdialer.data.ContactsSource
import com.smartdialer.data.DeviceContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads contacts that have a phone number. A contact's id is its stable lookup key. */
class DeviceContactsSource(private val resolver: ContentResolver) : ContactsSource {

    override suspend fun loadContacts(): List<DeviceContact>? = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(Phone.LOOKUP_KEY, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI)
            val contacts = LinkedHashMap<String, Contact>()
            val numbers = HashMap<String, MutableList<String>>()
            resolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val keyCol = cursor.getColumnIndexOrThrow(Phone.LOOKUP_KEY)
                val nameCol = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                val photoCol = cursor.getColumnIndexOrThrow(Phone.PHOTO_URI)
                while (cursor.moveToNext()) {
                    val key = cursor.getString(keyCol) ?: continue
                    val name = cursor.getString(nameCol)?.takeIf { it.isNotBlank() } ?: continue
                    contacts.getOrPut(key) { Contact(key, name, cursor.getString(photoCol)) }
                    cursor.getString(numberCol)?.let { numbers.getOrPut(key) { mutableListOf() }.add(it) }
                }
            }
            contacts.values.map { DeviceContact(it, numbers[it.id].orEmpty()) }
        } catch (_: SecurityException) {
            null
        }
    }
}
