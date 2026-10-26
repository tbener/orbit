package com.smartdialer.ranking

/**
 * The templates for the reasons the local engine writes. All wording lives here. Rules: one relevant fact in plain
 * language, no statistics, never a message count, and no "last spoke" wording unless it is the actual reason (a cadence
 * that is due, a same-day missed call to return, a habit tied to today).
 */
object ReasonTemplates {
    const val FREQUENCY = "You talk to them a lot."
    const val WHATSAPP = "Active WhatsApp conversation."
    const val MISSED_TODAY = "They called today and you haven't called back."
    const val MISSED_RECENT = "They called and you haven't called back."
    const val INITIATION = "You usually reach out to them."
    const val TIME_OF_DAY = "You usually talk around this time."
    const val INSTRUCTION = "Because of your note."

    fun weekday(dayName: String) = "You usually call on ${dayName}s."
    fun cadence(everyDays: Int) = "You wanted to talk every $everyDays days."
}

/** Builds a reason from the one or two features that added the most to a contact's score. */
object LocalReasons {

    fun reason(scored: ScoredContact): String {
        val parts = ArrayList<Pair<Double, String>>()
        for (c in scored.contributions) {
            if (c.points < ScoringDefaults.REASON_MIN_POINTS) continue
            val text = when (c.featureId) {
                FeatureIds.FREQUENCY -> ReasonTemplates.FREQUENCY
                FeatureIds.WHATSAPP -> ReasonTemplates.WHATSAPP
                FeatureIds.WEEKDAY_HABIT -> ReasonTemplates.weekday(scored.weekday.name.lowercase().replaceFirstChar(Char::uppercase))
                FeatureIds.TIME_OF_DAY_HABIT -> ReasonTemplates.TIME_OF_DAY
                FeatureIds.MISSED_CALL -> if (scored.lastMissedDaysAgo == 0) ReasonTemplates.MISSED_TODAY else ReasonTemplates.MISSED_RECENT
                FeatureIds.CADENCE_DUE -> scored.cadenceDays?.let(ReasonTemplates::cadence)
                FeatureIds.INITIATION -> ReasonTemplates.INITIATION
                else -> null
            } ?: continue
            parts += c.points to text
        }
        // A boost from the user's own note counts like a feature.
        val boost = scored.applied.sumOf { it.points }
        if (boost >= ScoringDefaults.REASON_MIN_POINTS) parts += boost to ReasonTemplates.INSTRUCTION
        val top = parts.sortedByDescending { it.first }.take(2).map { it.second }
        return when {
            top.isEmpty() -> ""
            else -> top.joinToString(" ").take(120)
        }
    }
}
