package com.smartdialer.ranking

/** An effect exactly as the model wrote it, before validation. Every field is present (empty when unused). */
class RawEffect(
    val type: String,
    val feature: String = "",
    val multiplier: Double = 1.0,
    val personName: String = "",
    val delta: Double = 0.0,
    val everyDays: Int = 0,
    val strength: Double = 1.0,
    val weekday: String = "",
    val part: String = "",
    val weekdays: List<String> = emptyList(),
    val parts: List<String> = emptyList(),
    val boost: Double = 0.0,
)

/** The outcome of validating one [RawEffect]: the clean effect, or why it was dropped. */
class EffectValidation(val params: EffectParams?, val issue: String?)

/**
 * Turns what the model proposed into safe effects. Out-of-range numbers are clamped; unknown types, unknown features
 * and contacts that cannot be resolved are dropped. Pure.
 */
object EffectValidator {

    /**
     * [contactKeyOf] resolves the person an effect is about to a contact key (the rule's own contact when the
     * rule is about one, otherwise by name), or null when unknown or ambiguous. [knownContactKeys] is every
     * contact on the device.
     */
    fun validate(raw: RawEffect, contactKeyOf: (personName: String) -> String?, knownContactKeys: Set<String>): EffectValidation {
        fun drop(why: String) = EffectValidation(null, why)
        fun contact(required: Boolean): Pair<String?, String?> {
            val key = contactKeyOf(raw.personName)
            return when {
                key == null && !required && raw.personName.isBlank() -> null to null
                key == null -> null to "no contact found for \"${raw.personName}\""
                key !in knownContactKeys -> null to "unknown contact"
                else -> key to null
            }
        }
        if (listOf(raw.multiplier, raw.delta, raw.strength, raw.boost).any { it.isNaN() || it.isInfinite() }) return drop("a number was not valid")

        return when (raw.type.trim().uppercase()) {
            "GLOBAL_WEIGHT" -> {
                val feature = FeatureIds.all.firstOrNull { it.equals(raw.feature.trim(), ignoreCase = true) } ?: return drop("unknown feature \"${raw.feature}\"")
                EffectValidation(
                    EffectParams.GlobalWeight(feature, raw.multiplier.coerceIn(ScoringDefaults.MULTIPLIER_MIN, ScoringDefaults.MULTIPLIER_MAX)),
                    null,
                )
            }
            "CONTACT_BOOST" -> {
                val (key, why) = contact(required = true)
                if (key == null) return drop(why ?: "no contact")
                val weekday = raw.weekday.takeIf { it.isNotBlank() }?.let { parseWeekday(it) ?: return drop("unknown weekday \"${raw.weekday}\"") }
                val part = raw.part.takeIf { it.isNotBlank() }?.let { PartOfDay.parse(it) ?: return drop("unknown part of day \"${raw.part}\"") }
                EffectValidation(
                    EffectParams.ContactBoost(key, raw.delta.coerceIn(ScoringDefaults.BOOST_MIN, ScoringDefaults.BOOST_MAX), weekday?.name, part?.name),
                    null,
                )
            }
            "CADENCE" -> {
                val (key, why) = contact(required = true)
                if (key == null) return drop(why ?: "no contact")
                EffectValidation(
                    EffectParams.Cadence(
                        key,
                        raw.everyDays.coerceIn(ScoringDefaults.CADENCE_DAYS_MIN, ScoringDefaults.CADENCE_DAYS_MAX),
                        raw.strength.coerceIn(0.1, 1.0),
                    ),
                    null,
                )
            }
            "TIME_WINDOW" -> {
                val (key, why) = contact(required = false)
                if (key == null && raw.personName.isNotBlank()) return drop(why ?: "no contact")
                val days = raw.weekdays.mapNotNull(::parseWeekday).map { it.name }.distinct()
                val parts = raw.parts.mapNotNull(PartOfDay::parse).map { it.name }.distinct()
                if (days.isEmpty() && parts.isEmpty()) return drop("a time window needs weekdays or parts of the day")
                EffectValidation(
                    EffectParams.TimeWindow(key, days, parts, raw.boost.coerceIn(ScoringDefaults.BOOST_MIN, ScoringDefaults.BOOST_MAX)),
                    null,
                )
            }
            else -> drop("unknown effect type \"${raw.type}\"")
        }
    }
}
