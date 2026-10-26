package com.smartdialer.ranking

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

/** WhatsApp facts for one contact. Null on a [ScoringInput] means the feature is off or has no data. */
data class WhatsAppFacts(
    val lastIncomingDaysAgo: Int?,
    val lastOutgoingFromOrbitDaysAgo: Int?,
    /** Message notifications in 30 days. Used inside the engine only; never shown to the user. */
    val incoming30d: Int,
)

/** Habit statistics from the call history (conversations only). */
data class HabitFacts(
    /** Weeks of history for this contact (from the first conversation, at most 12). */
    val observedWeeks: Int = 0,
    /** For each weekday: in how many of those weeks there was a conversation on it. */
    val weekdayHitWeeks: Map<DayOfWeek, Int> = emptyMap(),
    /** Conversations per part of the day, and their total. */
    val talksByPart: Map<PartOfDay, Int> = emptyMap(),
    val totalTalks: Int = 0,
) {
    companion object {
        val NONE = HabitFacts()
    }
}

/** Everything the engine knows about one contact. Names are for display only. */
data class ScoringInput(
    val contactKey: String,
    val name: String,
    val talks30d: Int = 0,
    val talks90d: Int = 0,
    /** Of all conversations in the window, how many the user started (outgoing). */
    val initiatedTalks: Int = 0,
    val totalTalks: Int = 0,
    val unreturnedMissed: Int = 0,
    val lastMissedDaysAgo: Int? = null,
    val lastContactDaysAgo: Int? = null,
    val whatsApp: WhatsAppFacts? = null,
    val habits: HabitFacts = HabitFacts.NONE,
)

/** What a feature sees besides the contact: the clock, the config and this contact's cadence (if it has one). */
class ScoringContext(
    val nowMillis: Long,
    val zone: ZoneId,
    val config: ScoringConfig,
    val cadence: EffectParams.Cadence?,
) {
    private val time = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val weekday: DayOfWeek = time.dayOfWeek
    val part: PartOfDay = PartOfDay.of(time.hour)
}

/**
 * One ingredient of the score. To add a feature (a place, for example): write an object implementing this, give it
 * an id, add it to [ScoringFeatures.all] and its weight to [ScoringConfig.DEFAULT_WEIGHTS]. Nothing else changes.
 */
interface ScoringFeature {
    val id: String

    /** 0..1, or null when the feature does not apply (it is then skipped, and contributes nothing). */
    fun value(input: ScoringInput, ctx: ScoringContext): Double?
}

object ScoringFeatures {
    val all: List<ScoringFeature> = listOf(Frequency, WhatsApp, WeekdayHabit, TimeOfDayHabit, MissedCall, CadenceDue, Initiation)

    private fun unit(x: Double) = x.coerceIn(0.0, 1.0)

    /** Conversations in the last 30 and 90 days. */
    object Frequency : ScoringFeature {
        override val id = FeatureIds.FREQUENCY
        override fun value(input: ScoringInput, ctx: ScoringContext): Double {
            val f30 = unit(input.talks30d.toDouble() / ctx.config.frequencyFull30d.coerceAtLeast(1))
            val f90 = unit(input.talks90d.toDouble() / ctx.config.frequencyFull90d.coerceAtLeast(1))
            return ScoringDefaults.FREQUENCY_SHARE_30D * f30 + (1 - ScoringDefaults.FREQUENCY_SHARE_30D) * f90
        }
    }

    /** Recent WhatsApp interaction: how recent the last incoming message is, and how active the conversation is. */
    object WhatsApp : ScoringFeature {
        override val id = FeatureIds.WHATSAPP
        override fun value(input: ScoringInput, ctx: ScoringContext): Double? {
            val wa = input.whatsApp ?: return null
            val days = ctx.config.whatsAppRecentDays.coerceAtLeast(1)
            val recency = wa.lastIncomingDaysAgo?.let { unit(1.0 - it.toDouble() / days) } ?: 0.0
            val outgoing = wa.lastOutgoingFromOrbitDaysAgo?.let { unit(1.0 - it.toDouble() / days) * 0.5 } ?: 0.0
            val activity = unit(wa.incoming30d.toDouble() / ScoringDefaults.WHATSAPP_FULL_ACTIVITY_30D)
            return ScoringDefaults.WHATSAPP_RECENCY_SHARE * maxOf(recency, outgoing) + (1 - ScoringDefaults.WHATSAPP_RECENCY_SHARE) * activity
        }
    }

    /** A reliable habit of talking on today's weekday: enough weeks of history and enough hits. */
    object WeekdayHabit : ScoringFeature {
        override val id = FeatureIds.WEEKDAY_HABIT
        override fun value(input: ScoringInput, ctx: ScoringContext): Double {
            val h = input.habits
            val hits = h.weekdayHitWeeks[ctx.weekday] ?: 0
            if (h.observedWeeks < ScoringDefaults.HABIT_MIN_WEEKS || hits < ScoringDefaults.HABIT_MIN_HITS) return 0.0
            return unit((hits.toDouble() / h.observedWeeks) / ScoringDefaults.HABIT_FULL_RATE)
        }
    }

    /** A reliable habit for the current part of the day: enough conversations and a high enough share in it. */
    object TimeOfDayHabit : ScoringFeature {
        override val id = FeatureIds.TIME_OF_DAY_HABIT
        override fun value(input: ScoringInput, ctx: ScoringContext): Double {
            val h = input.habits
            if (h.totalTalks < ScoringDefaults.TIME_OF_DAY_MIN_TALKS) return 0.0
            val share = (h.talksByPart[ctx.part] ?: 0).toDouble() / h.totalTalks
            if (share < ScoringDefaults.TIME_OF_DAY_MIN_SHARE) return 0.0
            return unit(share / ScoringDefaults.TIME_OF_DAY_FULL_SHARE)
        }
    }

    /** An unreturned missed call: full the same day, a little the day after, nothing later. */
    object MissedCall : ScoringFeature {
        override val id = FeatureIds.MISSED_CALL
        override fun value(input: ScoringInput, ctx: ScoringContext): Double {
            if (input.unreturnedMissed <= 0) return 0.0
            return when (input.lastMissedDaysAgo) {
                0 -> 1.0
                1 -> ctx.config.missedDayAfterFactor.coerceIn(0.0, 1.0)
                else -> 0.0
            }
        }
    }

    /**
     * How due a contact is against a cadence the user asked for. Only contacts with a cadence get any value: a long
     * gap by itself never raises a rank, and nothing is overdue unless a note says so.
     */
    object CadenceDue : ScoringFeature {
        override val id = FeatureIds.CADENCE_DUE
        override fun value(input: ScoringInput, ctx: ScoringContext): Double? {
            val cadence = ctx.cadence ?: return null
            val gap = input.lastContactDaysAgo ?: return unit(cadence.strength) // never talked: due from the start
            val share = gap.toDouble() / cadence.everyDays.coerceAtLeast(1)
            val due = unit((share - ScoringDefaults.CADENCE_DUE_START) / (ScoringDefaults.CADENCE_DUE_FULL - ScoringDefaults.CADENCE_DUE_START))
            return due * unit(cadence.strength)
        }
    }

    /** Contacts the user usually reaches out to first. */
    object Initiation : ScoringFeature {
        override val id = FeatureIds.INITIATION
        override fun value(input: ScoringInput, ctx: ScoringContext): Double {
            if (input.totalTalks < ScoringDefaults.INITIATION_MIN_TALKS) return 0.0
            return unit((input.initiatedTalks.toDouble() / input.totalTalks) / ScoringDefaults.INITIATION_FULL_SHARE)
        }
    }
}

enum class ScoreTier { TOP, HIGH, NORMAL, LOW }

/** One feature's part of a contact's score. */
data class Contribution(
    val featureId: String,
    /** The feature's value, 0..1. */
    val value: Double,
    val weight: Double,
    /** The product of the "global weight" effects for this feature (1 when there are none). */
    val multiplier: Double,
    val points: Double,
)

/** A boost from an instruction that applied to this contact now. */
data class AppliedEffect(val effectId: Long, val sourceRuleId: Long, val text: String, val points: Double)

data class ScoredContact(
    val contactKey: String,
    val name: String,
    /** 0..100. */
    val score: Double,
    val tier: ScoreTier,
    val contributions: List<Contribution>,
    val applied: List<AppliedEffect>,
    /** The score without the missed-call part. */
    val baseScore: Double,
    /** The cadence that applied, if the contact has one (for the reason). */
    val cadenceDays: Int?,
    val lastMissedDaysAgo: Int?,
    val weekday: DayOfWeek,
    val part: PartOfDay,
)

/** The local scoring engine. Pure and deterministic: the same inputs always give the same output. */
object ScoringEngine {

    fun score(
        inputs: List<ScoringInput>,
        effects: List<CompiledEffect>,
        config: ScoringConfig = ScoringConfig.DEFAULT,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        features: List<ScoringFeature> = ScoringFeatures.all,
        nameOf: (String) -> String = { it },
    ): List<ScoredContact> {
        val active = effects.filter { it.enabled }
        val multipliers = HashMap<String, Double>()
        for (e in active) {
            val p = e.params as? EffectParams.GlobalWeight ?: continue
            val m = p.multiplier.coerceIn(ScoringDefaults.MULTIPLIER_MIN, ScoringDefaults.MULTIPLIER_MAX)
            multipliers[p.feature] = ((multipliers[p.feature] ?: 1.0) * m).coerceIn(ScoringDefaults.MULTIPLIER_MIN, ScoringDefaults.MULTIPLIER_MAX)
        }
        val cadences = active.mapNotNull { it.params as? EffectParams.Cadence }.groupBy { it.contactKey }.mapValues { it.value.last() }

        val scored = inputs.map { input ->
            val ctx = ScoringContext(nowMillis, zone, config, cadences[input.contactKey])
            val contributions = features.mapNotNull { f ->
                val v = f.value(input, ctx) ?: return@mapNotNull null
                val weight = config.weight(f.id)
                val multiplier = multipliers[f.id] ?: 1.0
                var points = v * weight * multiplier
                // The hard cap: a missed call can never add more than its default weight, whatever else is set.
                if (f.id == FeatureIds.MISSED_CALL) points = min(points, ScoringDefaults.WEIGHT_MISSED_CALL)
                Contribution(f.id, v, weight, multiplier, points)
            }
            val applied = ArrayList<AppliedEffect>()
            for (e in active) {
                val extra: Double? = when (val p = e.params) {
                    is EffectParams.ContactBoost ->
                        if (p.contactKey == input.contactKey && matches(p.weekday?.let(::parseWeekday), p.part?.let(PartOfDay::parse), ctx)) {
                            p.delta.coerceIn(ScoringDefaults.BOOST_MIN, ScoringDefaults.BOOST_MAX)
                        } else null
                    is EffectParams.TimeWindow ->
                        if ((p.contactKey == null || p.contactKey == input.contactKey) &&
                            (p.weekdays.isEmpty() || p.weekdays.mapNotNull(::parseWeekday).contains(ctx.weekday)) &&
                            (p.parts.isEmpty() || p.parts.mapNotNull(PartOfDay::parse).contains(ctx.part))
                        ) {
                            p.boost.coerceIn(ScoringDefaults.BOOST_MIN, ScoringDefaults.BOOST_MAX)
                        } else null
                    else -> null
                }
                if (extra != null && extra != 0.0) applied += AppliedEffect(e.id, e.sourceRuleId, EffectText.describe(e.params, nameOf), extra)
            }
            val missedPoints = contributions.filter { it.featureId == FeatureIds.MISSED_CALL }.sumOf { it.points }
            val base = (contributions.sumOf { it.points } - missedPoints + applied.sumOf { it.points }).coerceAtLeast(0.0)
            val total = (base + missedPoints).coerceIn(0.0, 100.0)
            ScoredContact(
                contactKey = input.contactKey,
                name = input.name,
                score = total,
                tier = tierOf(total),
                contributions = contributions,
                applied = applied,
                baseScore = base.coerceAtMost(100.0),
                cadenceDays = ctx.cadence?.everyDays,
                lastMissedDaysAgo = input.lastMissedDaysAgo,
                weekday = ctx.weekday,
                part = ctx.part,
            )
        }
        // A contact whose score is mostly a missed call never outranks one that has a real reason: contacts with enough
        // of a base score come first, so a missed call alone cannot put anyone at #1.
        val (eligible, rest) = scored.partition { it.baseScore >= config.minBaseForTop }
        val order = compareByDescending<ScoredContact> { it.score }.thenBy { it.name.lowercase() }
        return if (eligible.isEmpty()) scored.sortedWith(order) else eligible.sortedWith(order) + rest.sortedWith(order)
    }

    private fun matches(day: DayOfWeek?, part: PartOfDay?, ctx: ScoringContext) =
        (day == null || day == ctx.weekday) && (part == null || part == ctx.part)

    private fun tierOf(score: Double) = when {
        score >= ScoringDefaults.TIER_TOP -> ScoreTier.TOP
        score >= ScoringDefaults.TIER_HIGH -> ScoreTier.HIGH
        score >= ScoringDefaults.TIER_NORMAL -> ScoreTier.NORMAL
        else -> ScoreTier.LOW
    }
}
