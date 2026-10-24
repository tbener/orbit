package com.smartdialer.data

/** [id] is the contacts lookup key: stable across contact merges, unlike the numeric contact id. */
data class Contact(
    val id: String,
    val name: String,
    val photoUri: String? = null,
)

enum class SuggestionBadge { MissedCall }

/** A contact the app suggests calling, with the short texts shown on the home screen. */
data class Suggestion(
    val contact: Contact,
    val subtitle: String?,
    val badge: SuggestionBadge?,
    val reason: String,
    /** True when a pin rule put this contact in its slot. */
    val isPinned: Boolean = false,
)

/** A contact as stored on the device, with every phone number it has. */
data class DeviceContact(
    val contact: Contact,
    val numbers: List<String>,
)

enum class CallKind { Incoming, Outgoing, Missed, Other }

/** One entry from the device call log. */
data class CallRecord(
    val number: String,
    val kind: CallKind,
    val timestampMillis: Long,
    val durationSeconds: Long,
)
