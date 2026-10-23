package com.smartdialer.data

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackRankerTest {

    private val now = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L

    private fun contact(id: String, name: String, vararg numbers: String) =
        DeviceContact(Contact(id, name), numbers.toList())

    private fun call(number: String, kind: CallKind, daysAgo: Long) =
        CallRecord(number, kind, now - daysAgo * day, durationSeconds = 60)

    private fun rank(contacts: List<DeviceContact>, calls: List<CallRecord>) =
        FallbackRanker.rank(contacts, calls, now, ZoneOffset.UTC)

    @Test
    fun missedCallComesFirstAndGetsBadge() {
        val contacts = listOf(contact("1", "Mom", "0501111111"), contact("2", "Dana", "0502222222"))
        val calls = listOf(
            call("0501111111", CallKind.Outgoing, daysAgo = 40),
            call("0502222222", CallKind.Missed, daysAgo = 1),
        )
        val result = rank(contacts, calls)
        assertEquals("Dana", result.first().contact.name)
        assertEquals(SuggestionBadge.MissedCall, result.first().badge)
        assertEquals("Missed", result.first().subtitle)
    }

    @Test
    fun laterOutgoingCallClearsTheCallback() {
        val contacts = listOf(contact("1", "Dana", "0502222222"))
        val calls = listOf(
            call("0502222222", CallKind.Outgoing, daysAgo = 0),
            call("0502222222", CallKind.Missed, daysAgo = 1),
        )
        assertNull(rank(contacts, calls).single().badge)
    }

    @Test
    fun moreOverdueAgainstOwnRhythmRanksHigher() {
        val contacts = listOf(contact("1", "Mom", "0501111111"), contact("2", "Avi", "0503333333"))
        val calls = listOf(
            // Mom: talks every ~3 days, last talk 6 days ago -> twice overdue.
            call("0501111111", CallKind.Outgoing, 6),
            call("0501111111", CallKind.Outgoing, 9),
            call("0501111111", CallKind.Outgoing, 12),
            call("0501111111", CallKind.Outgoing, 15),
            // Avi: no rhythm (default 14 days), last talk 7 days ago -> half overdue.
            call("0503333333", CallKind.Outgoing, 7),
        )
        val result = rank(contacts, calls)
        assertEquals(listOf("Mom", "Avi"), result.map { it.contact.name })
        assertEquals("You usually talk every 3 days, and it's been 6.", result.first().reason)
    }

    @Test
    fun numbersInDifferentFormatsMatchTheSameContact() {
        val contacts = listOf(contact("1", "Ron", "050-111-1111"))
        val calls = listOf(call("+972501111111", CallKind.Incoming, 2))
        assertEquals("Ron", rank(contacts, calls).single().contact.name)
    }

    @Test
    fun callsFromUnknownNumbersAreIgnored() {
        val contacts = listOf(contact("1", "Ron", "0501111111"))
        val calls = listOf(call("0509999999", CallKind.Missed, 1))
        assertTrue(rank(contacts, calls).isEmpty())
    }

    @Test
    fun contactsWithoutCallsAreNotSuggested() {
        assertTrue(rank(listOf(contact("1", "Ron", "0501111111")), emptyList()).isEmpty())
    }
}
