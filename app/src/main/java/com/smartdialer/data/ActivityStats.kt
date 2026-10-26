package com.smartdialer.data

import com.smartdialer.ranking.HabitFacts
import com.smartdialer.ranking.PartOfDay
import com.smartdialer.ranking.ScoringInput
import com.smartdialer.ranking.WhatsAppFacts
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * What the local scoring engine needs from a contact's call history, beyond [ContactSignals]: conversation counts
 * over 30 and 90 days, how many the user started, and the weekday and part-of-day habits. "Conversation" means an
 * answered call of at least [ContactSignals.MIN_TALK_SECONDS].
 */
data class ActivityStats(
    val talks30d: Int = 0,
    val talks90d: Int = 0,
    /** Of the conversations in the last 90 days, how many the user started. */
    val initiatedTalks90d: Int = 0,
    val habits: HabitFacts = HabitFacts.NONE,
) {
    companion object {
        val EMPTY = ActivityStats()
        private const val DAY = 24L * 60 * 60 * 1000
        private const val HABIT_WEEKS = 12

        fun from(records: List<CallRecord>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ActivityStats {
            val talks = records.filter(ContactSignals::isTalk)
            if (talks.isEmpty()) return EMPTY
            val in30 = talks.count { it.timestampMillis >= nowMillis - 30 * DAY }
            val in90 = talks.filter { it.timestampMillis >= nowMillis - 90 * DAY }
            val initiated = in90.count { it.kind == CallKind.Outgoing }

            // Habits look at the last 12 weeks.
            val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            val thisWeek = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().with(monday)
            val recent = talks.filter { it.timestampMillis >= nowMillis - HABIT_WEEKS * 7 * DAY }
            if (recent.isEmpty()) return ActivityStats(in30, in90.size, initiated)
            val dated = recent.map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone) }
            val weekOf = { z: java.time.ZonedDateTime -> ChronoUnit.WEEKS.between(z.toLocalDate().with(monday), thisWeek) }
            val observed = (dated.maxOf(weekOf) + 1).toInt().coerceIn(1, HABIT_WEEKS)
            val weekdayWeeks: Map<DayOfWeek, Int> = dated.groupBy { it.dayOfWeek }.mapValues { (_, list) -> list.map(weekOf).distinct().size }
            val parts: Map<PartOfDay, Int> = dated.groupingBy { PartOfDay.of(it.hour) }.eachCount()
            return ActivityStats(in30, in90.size, initiated, HabitFacts(observed, weekdayWeeks, parts, recent.size))
        }
    }
}

/** Turns what Orbit knows into inputs for the scoring engine. Pure. */
object ScoringInputs {

    /**
     * One input per contact that has call history or WhatsApp activity, plus [extraKeys] (contacts an instruction
     * mentions). WhatsApp facts are included only when [whatsAppEnabled] and the contact has some.
     */
    fun build(
        contacts: List<Contact>,
        activity: Map<String, ContactActivity>,
        whatsAppEnabled: Boolean,
        nowMillis: Long,
        zone: ZoneId,
        extraKeys: Set<String> = emptySet(),
    ): List<ScoringInput> = contacts.mapNotNull { contact ->
        val a = activity[contact.id]
        if (a == null && contact.id !in extraKeys) return@mapNotNull null
        val s = a?.signals
        val stats = a?.stats ?: ActivityStats.EMPTY
        fun days(millis: Long?) = millis?.let { FallbackRanker.daysBetween(it, nowMillis, zone).toInt() }
        ScoringInput(
            contactKey = contact.id,
            name = contact.name,
            talks30d = stats.talks30d,
            talks90d = stats.talks90d,
            initiatedTalks = stats.initiatedTalks90d,
            totalTalks = stats.talks90d,
            unreturnedMissed = s?.unreturnedMissedCalls ?: 0,
            lastMissedDaysAgo = days(s?.lastMissedMillis),
            lastContactDaysAgo = days(s?.lastContactMillis),
            whatsApp = if (whatsAppEnabled && s != null && s.hasWhatsApp) {
                WhatsAppFacts(days(s.lastWhatsAppIncomingAt), days(s.lastWhatsAppOutgoingFromOrbitAt), s.whatsAppIncoming30d)
            } else null,
            habits = stats.habits,
        )
    }.filter { it.talks90d > 0 || it.unreturnedMissed > 0 || it.whatsApp != null || it.contactKey in extraKeys || it.lastContactDaysAgo != null }
}
