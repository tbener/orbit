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
) {
    companion object {
        fun from(records: List<CallRecord>): ContactSignals {
            val handled = records.filter { it.kind == CallKind.Incoming || it.kind == CallKind.Outgoing }
            val lastContact = handled.maxOfOrNull { it.timestampMillis }
            val unreturned = records.filter {
                it.kind == CallKind.Missed && (lastContact == null || it.timestampMillis > lastContact)
            }
            return ContactSignals(
                lastContactMillis = lastContact,
                unreturnedMissedCalls = unreturned.size,
                lastMissedMillis = unreturned.maxOfOrNull { it.timestampMillis },
                callCount = records.size,
            )
        }
    }
}

/** Call-log derived data for one contact. */
data class ContactActivity(
    val signals: ContactSignals,
    /** When each of the contact's numbers (by [CallMatcher.numberKey]) was last used. */
    val lastUsedByNumberKey: Map<String, Long>,
)

/** Plain-language lines describing [ContactSignals], for the Details screen. */
object SignalSummary {
    fun lines(
        name: String,
        signals: ContactSignals?,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> {
        if (signals == null || signals.callCount == 0) {
            return listOf("No calls with $name in the recent call history.")
        }
        val lines = mutableListOf<String>()
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
        return lines
    }
}
