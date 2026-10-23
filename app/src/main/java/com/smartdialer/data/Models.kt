package com.smartdialer.data

data class Contact(
    val id: String,
    val name: String,
)

enum class SuggestionBadge { MissedCall }

/** A contact the app suggests calling, with the short texts shown on the home screen. */
data class Suggestion(
    val contact: Contact,
    val subtitle: String?,
    val badge: SuggestionBadge?,
    val reason: String,
)

data class SuggestionPage(
    val items: List<Suggestion>,
    val hasMore: Boolean,
)
