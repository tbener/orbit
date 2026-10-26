package com.smartdialer.ranking

/**
 * Every default weight, cap and decay of the local scoring engine, in one place. **Each value is a starting guess**:
 * tune them on the weight tuning screen (Settings > Advanced) and paste the result back to refine these.
 *
 * Weights are in points on a 0..100 scale: a feature's weight is the most points it can add. A contact's score is the
 * sum of its features' points (plus any boosts from the user's instructions), clamped to 0..100.
 */
object ScoringDefaults {
    // ---- Feature weights (the most points each feature can add)

    /** Frequency of calls over the last 30 and 90 days. The dominant signal. */
    const val WEIGHT_FREQUENCY = 45.0

    /** Recent WhatsApp interaction. A strong signal; skipped when the WhatsApp feature is off or has no data. */
    const val WEIGHT_WHATSAPP = 25.0

    /** A reliable habit of talking on today's weekday. */
    const val WEIGHT_WEEKDAY_HABIT = 12.0

    /** A reliable habit of talking at this part of the day. Small. */
    const val WEIGHT_TIME_OF_DAY_HABIT = 5.0

    /** An unreturned missed call. Weak, and decays fast. This is also the hard cap on what it can ever add. */
    const val WEIGHT_MISSED_CALL = 10.0

    /** How due a contact is against a cadence the user asked for. Only contacts with a cadence note get it. */
    const val WEIGHT_CADENCE_DUE = 40.0

    /** Contacts the user usually reaches out to first. Small. */
    const val WEIGHT_INITIATION = 5.0

    // ---- Frequency

    /** This many conversations in 30 days count as "full" frequency for the 30-day part. */
    const val FREQUENCY_FULL_30D = 8

    /** This many conversations in 90 days count as full for the 90-day part. */
    const val FREQUENCY_FULL_90D = 20

    /** How much of the frequency feature comes from the last 30 days (the rest from 90 days). */
    const val FREQUENCY_SHARE_30D = 0.6

    // ---- WhatsApp

    /** An incoming WhatsApp message fades out over this many days. */
    const val WHATSAPP_RECENT_DAYS = 7

    /** This many message notifications in 30 days count as a fully active conversation (internal; never shown). */
    const val WHATSAPP_FULL_ACTIVITY_30D = 20

    /** How much of the WhatsApp feature is recency (the rest is how active the conversation is). */
    const val WHATSAPP_RECENCY_SHARE = 0.6

    // ---- Habits

    /** A weekday habit needs this many weeks of call history for the contact... */
    const val HABIT_MIN_WEEKS = 4

    /** ...and at least this many of those weeks with a conversation on that weekday. */
    const val HABIT_MIN_HITS = 3

    /** A hit rate at or above this counts as a full habit. */
    const val HABIT_FULL_RATE = 0.8

    /** A time-of-day habit needs at least this many conversations... */
    const val TIME_OF_DAY_MIN_TALKS = 5

    /** ...with at least this share in the current part of the day. */
    const val TIME_OF_DAY_MIN_SHARE = 0.5

    /** A share at or above this counts as a full time-of-day habit. */
    const val TIME_OF_DAY_FULL_SHARE = 0.8

    // ---- Missed call

    /** How much of a missed call still counts the day after it (the same day counts fully; later nothing). */
    const val MISSED_DAY_AFTER_FACTOR = 0.35

    /**
     * A contact whose score without the missed-call part is below this is never ranked above contacts that reach it,
     * so a missed call alone can never put someone at #1.
     */
    const val MIN_BASE_FOR_TOP = 12.0

    // ---- Cadence

    /** Due-ness starts at this share of the cadence (0.7 means after 70% of the interval)... */
    const val CADENCE_DUE_START = 0.7

    /** ...and is full at this share (1.3 means 30% past the interval). */
    const val CADENCE_DUE_FULL = 1.3

    // ---- Initiation

    /** Initiation needs at least this many conversations to count. */
    const val INITIATION_MIN_TALKS = 5

    /** A share of conversations the user started at or above this is full initiation. */
    const val INITIATION_FULL_SHARE = 0.8

    // ---- Tiers (by score)

    const val TIER_TOP = 70.0
    const val TIER_HIGH = 50.0
    const val TIER_NORMAL = 25.0

    // ---- Reasons

    /** A feature must add at least this many points to be named in a reason. */
    const val REASON_MIN_POINTS = 3.0

    /** Bounds for the instruction effects (values outside are clamped). */
    const val MULTIPLIER_MIN = 0.0
    const val MULTIPLIER_MAX = 3.0
    const val BOOST_MIN = -40.0
    const val BOOST_MAX = 40.0
    const val CADENCE_DAYS_MIN = 1
    const val CADENCE_DAYS_MAX = 365
}

/** The values the weight tuning screen can change. Everything else stays at its default. */
data class ScoringConfig(
    val weights: Map<String, Double> = DEFAULT_WEIGHTS,
    val missedDayAfterFactor: Double = ScoringDefaults.MISSED_DAY_AFTER_FACTOR,
    val frequencyFull30d: Int = ScoringDefaults.FREQUENCY_FULL_30D,
    val frequencyFull90d: Int = ScoringDefaults.FREQUENCY_FULL_90D,
    val whatsAppRecentDays: Int = ScoringDefaults.WHATSAPP_RECENT_DAYS,
    val minBaseForTop: Double = ScoringDefaults.MIN_BASE_FOR_TOP,
) {
    fun weight(featureId: String): Double = weights[featureId] ?: DEFAULT_WEIGHTS[featureId] ?: 0.0

    /** The settings as plain text, for pasting into a chat. */
    fun asText(): String = buildString {
        FeatureIds.all.forEach { appendLine("weight.$it = ${"%.1f".format(weight(it))}") }
        appendLine("missedDayAfterFactor = ${"%.2f".format(missedDayAfterFactor)}")
        appendLine("frequencyFull30d = $frequencyFull30d")
        appendLine("frequencyFull90d = $frequencyFull90d")
        appendLine("whatsAppRecentDays = $whatsAppRecentDays")
        appendLine("minBaseForTop = ${"%.1f".format(minBaseForTop)}")
    }

    companion object {
        val DEFAULT_WEIGHTS: Map<String, Double> = mapOf(
            FeatureIds.FREQUENCY to ScoringDefaults.WEIGHT_FREQUENCY,
            FeatureIds.WHATSAPP to ScoringDefaults.WEIGHT_WHATSAPP,
            FeatureIds.WEEKDAY_HABIT to ScoringDefaults.WEIGHT_WEEKDAY_HABIT,
            FeatureIds.TIME_OF_DAY_HABIT to ScoringDefaults.WEIGHT_TIME_OF_DAY_HABIT,
            FeatureIds.MISSED_CALL to ScoringDefaults.WEIGHT_MISSED_CALL,
            FeatureIds.CADENCE_DUE to ScoringDefaults.WEIGHT_CADENCE_DUE,
            FeatureIds.INITIATION to ScoringDefaults.WEIGHT_INITIATION,
        )
        val DEFAULT = ScoringConfig()
    }
}

object FeatureIds {
    const val FREQUENCY = "frequency"
    const val WHATSAPP = "whatsapp"
    const val WEEKDAY_HABIT = "weekdayHabit"
    const val TIME_OF_DAY_HABIT = "timeOfDayHabit"
    const val MISSED_CALL = "missedCall"
    const val CADENCE_DUE = "cadenceDue"
    const val INITIATION = "initiation"
    val all = listOf(FREQUENCY, WHATSAPP, WEEKDAY_HABIT, TIME_OF_DAY_HABIT, MISSED_CALL, CADENCE_DUE, INITIATION)
}
