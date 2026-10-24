package com.smartdialer.data

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactSignalsTest {

    private val now = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L

    private fun call(kind: CallKind, daysAgo: Long) = CallRecord("0501111111", kind, now - daysAgo * day, 30)

    @Test
    fun lastContactIgnoresMissedCalls() {
        val s = ContactSignals.from(listOf(call(CallKind.Missed, 1), call(CallKind.Outgoing, 5), call(CallKind.Incoming, 9)))
        assertEquals(now - 5 * day, s.lastContactMillis)
    }

    @Test
    fun missedCallsAfterTheLastConversationAreUnreturned() {
        val s = ContactSignals.from(
            listOf(call(CallKind.Missed, 1), call(CallKind.Missed, 2), call(CallKind.Outgoing, 5), call(CallKind.Missed, 8)),
        )
        assertEquals(2, s.unreturnedMissedCalls)
        assertEquals(now - 1 * day, s.lastMissedMillis)
    }

    @Test
    fun aLaterOutgoingCallReturnsThem() {
        val s = ContactSignals.from(listOf(call(CallKind.Outgoing, 0), call(CallKind.Missed, 1)))
        assertEquals(0, s.unreturnedMissedCalls)
        assertNull(s.lastMissedMillis)
    }

    @Test
    fun onlyMissedCalls_allUnreturnedAndNeverSpoke() {
        val s = ContactSignals.from(listOf(call(CallKind.Missed, 3), call(CallKind.Missed, 4)))
        assertNull(s.lastContactMillis)
        assertEquals(2, s.unreturnedMissedCalls)
    }

    @Test
    fun summaryDescribesLastContactAndMissedCalls() {
        val signals = ContactSignals.from(listOf(call(CallKind.Missed, 1), call(CallKind.Outgoing, 3)))
        assertEquals(
            listOf("Last spoke 3 days ago.", "1 missed call not returned (latest yesterday)."),
            SignalSummary.lines("Mom", signals, now, ZoneOffset.UTC),
        )
    }

    @Test
    fun summaryWithoutHistory() {
        assertEquals(
            listOf("No calls with Mom in the recent call history."),
            SignalSummary.lines("Mom", null, now, ZoneOffset.UTC),
        )
    }

    @Test
    fun summaryWhenNothingIsWaiting() {
        val signals = ContactSignals.from(listOf(call(CallKind.Incoming, 0)))
        assertEquals(
            listOf("Last spoke today.", "No missed calls waiting for a callback."),
            SignalSummary.lines("Mom", signals, now, ZoneOffset.UTC),
        )
    }
}
