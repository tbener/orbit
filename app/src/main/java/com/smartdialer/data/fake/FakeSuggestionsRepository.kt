package com.smartdialer.data.fake

import com.smartdialer.data.Contact
import com.smartdialer.data.MAX_SUGGESTIONS
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionBadge
import com.smartdialer.data.SuggestionsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hardcoded in-memory data, handy for previews and for developing without a populated phone. */
class FakeSuggestionsRepository : SuggestionsRepository {

    private val _isRefreshing = MutableStateFlow(false)
    override val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    override val suggestions: StateFlow<List<Suggestion>> =
        MutableStateFlow(fakeSuggestions.take(MAX_SUGGESTIONS)).asStateFlow()

    override suspend fun searchContacts(query: String): List<Contact> {
        val q = query.trim()
        return allContacts.filter { it.name.contains(q, ignoreCase = true) }
    }

    override suspend fun refresh() {
        _isRefreshing.value = true
        delay(3_000)
        _isRefreshing.value = false
    }

    private companion object {
        fun s(id: String, name: String, subtitle: String?, reason: String, badge: SuggestionBadge? = null) =
            Suggestion(Contact(id, name), subtitle, badge, reason)

        val fakeSuggestions = listOf(
            s("mom", "Mom", "3 days", "You wanted to talk every 3 days, and it's been 3."),
            s("dana", "Dana", "Missed", "Dana called yesterday evening and you haven't called back yet.", SuggestionBadge.MissedCall),
            s("ron", "Ron", "Sunday", "You usually call Ron on Sundays."),
            s("yael", "Yael", null, "A close friend. You haven't spoken in about 5 weeks."),
            s("avi", "Avi", null, "You spoke twice last month, less than usual."),
            s("nir", "Nir", null, "It has been a while since your last call."),
            s("tamar", "Tamar", null, "You often call Tamar on weekday evenings."),
            s("noam", "Noam", "2 weeks", "Your brother. Last call was two weeks ago."),
            s("shira", "Shira", null, "You exchanged calls last week but didn't connect."),
            s("omer", "Omer", null, "A friend you called regularly earlier this year."),
            s("lior", "Lior", null, "Last call was about a month ago."),
            s("maya", "Maya", "Friday", "You often call Maya on Fridays."),
            s("itay", "Itay", null, "A friend you haven't spoken to recently."),
            s("roni", "Roni", null, "Last call was about six weeks ago."),
            s("gil", "Gil", null, "You usually talk once a month."),
            s("tal", "Tal", null, "Last call was a while ago."),
            s("ofir", "Ofir", null, "Occasional contact."),
            s("hila", "Hila", null, "Occasional contact."),
        )

        val allContacts = (
            fakeSuggestions.map { it.contact } + listOf(
                Contact("eli", "Eli (Dentist)"),
                Contact("gal", "Gal (Work)"),
                Contact("amir", "Amir"),
                Contact("batya", "Batya"),
                Contact("carmel", "Carmel"),
                Contact("dor", "Dor"),
            )
        ).sortedBy { it.name }
    }
}
