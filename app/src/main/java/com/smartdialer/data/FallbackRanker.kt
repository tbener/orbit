package com.smartdialer.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Simple deterministic ranking used until LLM results are available (and as a permanent fallback).
 * Missed calls awaiting a callback come first, then contacts by how overdue they are against their
 * own usual rhythm. Hard rules are NOT applied here; that is the job of the ranking package.
 */
object FallbackRanker {
    private const val DEFAULT_GAP_DAYS = 14.0
    private const val MIN_EVENTS_FOR_RHYTHM = 3
    private const val CALLBACK_BASE_SCORE = 1000.0

    private class Scored(val score: Double, val suggestion: Suggestion)

    fun rank(
        contacts: List<DeviceContact>,
        calls: List<CallRecord>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Suggestion> {
        val callsByContact = CallMatcher.groupByContact(contacts, calls)
        val contactById = contacts.associateBy { it.contact.id }

        return callsByContact
            .mapNotNull { (id, records) -> contactById[id]?.let { score(it.contact, records, nowMillis, zone) } }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.suggestion.contact.name })
            .map { it.suggestion }
    }

    private fun score(contact: Contact, records: List<CallRecord>, nowMillis: Long, zone: ZoneId): Scored {
        val sorted = records.sortedByDescending { it.timestampMillis }
        val newest = sorted.first()
        val events = sorted.filter(ContactSignals::isTalk)
        val needsCallback = newest.kind == CallKind.Missed
        val lastTalkMillis = (events.firstOrNull() ?: newest).timestampMillis
        val daysSince = daysBetween(lastTalkMillis, nowMillis, zone)

        if (needsCallback) {
            val daysSinceMissed = daysBetween(newest.timestampMillis, nowMillis, zone)
            return Scored(
                score = CALLBACK_BASE_SCORE - daysSinceMissed.coerceAtMost(30),
                suggestion = Suggestion(
                    contact = contact,
                    subtitle = if (daysSinceMissed == 0L) "Missed today" else "Missed ${daysSinceMissed}d",
                    badge = SuggestionBadge.MissedCall,
                    reason = "${contact.name} called ${agoPhrase(daysSinceMissed)} and you haven't called back.",
                ),
            )
        }

        val gapDays = typicalGapDays(events)
        val reason = when {
            daysSince == 0L -> "You spoke today."
            gapDays != null -> "You usually talk every ${gapDays.roundToInt()} days, and it's been $daysSince."
            else -> "You last spoke ${agoPhrase(daysSince)}."
        }
        return Scored(
            score = daysSince / (gapDays ?: DEFAULT_GAP_DAYS),
            suggestion = Suggestion(contact, subtitle = shortAgo(daysSince), badge = null, reason = reason),
        )
    }

    /** Median number of days between consecutive talks, or null when there is too little history. */
    private fun typicalGapDays(eventsNewestFirst: List<CallRecord>): Double? {
        if (eventsNewestFirst.size < MIN_EVENTS_FOR_RHYTHM) return null
        val gaps = eventsNewestFirst.zipWithNext { newer, older ->
            (newer.timestampMillis - older.timestampMillis) / MILLIS_PER_DAY
        }.sorted()
        val median = if (gaps.size % 2 == 1) gaps[gaps.size / 2] else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2
        return median.coerceIn(1.0, 60.0)
    }

    private const val MILLIS_PER_DAY = 86_400_000.0

    internal fun daysBetween(fromMillis: Long, toMillis: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate(),
        ).coerceAtLeast(0)

    internal fun agoPhrase(days: Long): String = when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }

    private fun shortAgo(days: Long): String = when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 14 -> "$days days"
        days < 60 -> "${days / 7} weeks"
        else -> "${days / 30} months"
    }
}
