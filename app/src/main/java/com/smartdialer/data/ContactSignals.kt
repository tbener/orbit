package com.smartdialer.data

import java.time.ZoneId

/** Facts about one contact derived from the call log. Shown on the Details screen; stage 4 sends them to the LLM. */
data class ContactSignals(
    /** When the user last actually talked to this contact (an incoming or outgoing call), if ever. */
    val lastContactMillis: Long?,
    /** Missed calls from this contact that the user has not answered or returned since. */
    val unreturnedMissedCalls: Int,
    /** When the newest unreturned missed call happened. */
    val lastMissedMillis: Long?,
    /** Number of call-log entries for this contact in the history window. */
    val callCount: Int,
    /** WhatsApp (see [WhatsAppEvent]): incoming message notifications, and chats started from Orbit. Partial data. */
    val lastWhatsAppIncomingAt: Long? = null,
    val lastWhatsAppOutgoingFromOrbitAt: Long? = null,
    val whatsAppIncoming7d: Int = 0,
    val whatsAppIncoming30d: Int = 0,
    /** Real conversations: answered incoming or outgoing calls of at least [MIN_TALK_SECONDS]. */
    val talkCount: Int = 0,
    /** Missed calls from this contact in the history window, returned or not. */
    val missedCount: Int = 0,
    /** Outgoing calls that were not answered (shorter than [MIN_TALK_SECONDS]). */
    val unansweredOutgoingCount: Int = 0,
    /** Calls of any kind in the last 90 days, and the time of the newest call of any kind (for the search suggestions). */
    val callsLast90d: Int = 0,
    val lastCallAt: Long? = null,
) {
    val hasWhatsApp: Boolean get() = lastWhatsAppIncomingAt != null || lastWhatsAppOutgoingFromOrbitAt != null

    companion object {
        /** A call shorter than this was not a conversation (it rang out or was cut off). */
        const val MIN_TALK_SECONDS = 10L

        fun isTalk(record: CallRecord): Boolean =
            (record.kind == CallKind.Incoming || record.kind == CallKind.Outgoing) && record.durationSeconds >= MIN_TALK_SECONDS

        private const val NINETY_DAYS = 90L * 24 * 60 * 60 * 1000

        fun from(records: List<CallRecord>, nowMillis: Long = System.currentTimeMillis()): ContactSignals {
            val talks = records.filter(::isTalk)
            val lastContact = talks.maxOfNullable { it.timestampMillis }
            // A missed call counts as answered once the user spoke with them or tried calling them back.
            val lastHandled = records.filter { isTalk(it) || it.kind == CallKind.Outgoing }.maxOfNullable { it.timestampMillis }
            val unreturned = records.filter {
                it.kind == CallKind.Missed && (lastHandled == null || it.timestampMillis > lastHandled)
            }
            return ContactSignals(
                lastContactMillis = lastContact,
                unreturnedMissedCalls = unreturned.size,
                lastMissedMillis = unreturned.maxOfNullable { it.timestampMillis },
                callCount = records.size,
                talkCount = talks.size,
                missedCount = records.count { it.kind == CallKind.Missed },
                unansweredOutgoingCount = records.count { it.kind == CallKind.Outgoing && !isTalk(it) },
                callsLast90d = records.count { it.timestampMillis >= nowMillis - NINETY_DAYS },
                lastCallAt = records.maxOfNullable { it.timestampMillis },
            )
        }

        private inline fun <T> List<T>.maxOfNullable(selector: (T) -> Long): Long? = maxOfOrNull(selector)
    }
}

/** Call-log derived data for one contact. */
data class ContactActivity(
    val signals: ContactSignals,
    /** When each of the contact's numbers (by [CallMatcher.numberKey]) was last used. */
    val lastUsedByNumberKey: Map<String, Long>,
    /** Conversation counts and habits for the local scoring engine. */
    val stats: ActivityStats = ActivityStats.EMPTY,
)

/** Plain-language lines describing [ContactSignals], for the Details screen. */
object SignalSummary {
    fun lines(
        name: String,
        signals: ContactSignals?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> {
        if (signals == null || (signals.callCount == 0 && !signals.hasWhatsApp)) {
            return listOf("No calls with $name in the recent call history.")
        }
        val lines = mutableListOf<String>()
        if (signals.callCount == 0) {
            lines += "No calls with $name in the recent call history."
            lines += whatsAppLines(signals, nowMillis, zone)
            return lines
        }
        lines += signals.lastContactMillis
            ?.let { "Last spoke ${FallbackRanker.agoPhrase(FallbackRanker.daysBetween(it, nowMillis, zone))}." }
            ?: "You have not spoken to $name in the recent call history."
        if (signals.unreturnedMissedCalls > 0 && signals.lastMissedMillis != null) {
            val count = signals.unreturnedMissedCalls
            val latest = FallbackRanker.agoPhrase(FallbackRanker.daysBetween(signals.lastMissedMillis, nowMillis, zone))
            lines += "$count missed ${if (count == 1) "call" else "calls"} not returned (latest $latest)."
        } else {
            lines += "No missed calls waiting for a callback."
        }
        lines += whatsAppLines(signals, nowMillis, zone)
        return lines
    }

    /** Plain-language WhatsApp lines. Only present when there is data (and the feature is on). */
    private fun whatsAppLines(signals: ContactSignals, nowMillis: Long, zone: ZoneId): List<String> = buildList {
        signals.lastWhatsAppIncomingAt?.let {
            add("Messaged you on WhatsApp ${FallbackRanker.agoPhrase(FallbackRanker.daysBetween(it, nowMillis, zone))}.")
        }
        signals.lastWhatsAppOutgoingFromOrbitAt?.let {
            add("You opened WhatsApp for them from Orbit ${FallbackRanker.agoPhrase(FallbackRanker.daysBetween(it, nowMillis, zone))}.")
        }
    }
}
