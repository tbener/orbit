package com.smartdialer.ranking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

/** When in the day. The same four parts are used for habits and for instruction time windows. */
enum class PartOfDay {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun of(hour: Int): PartOfDay = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }

        fun parse(text: String?): PartOfDay? = entries.firstOrNull { it.name.equals(text?.trim(), ignoreCase = true) }
    }
}

fun parseWeekday(text: String?): DayOfWeek? = DayOfWeek.entries.firstOrNull { it.name.equals(text?.trim(), ignoreCase = true) }

/**
 * What an instruction can do to the score. Each is validated and clamped when it is created
 * (see `EffectValidator`), and the engine clamps again, so a bad effect can never break the ranking.
 */
@Serializable
sealed interface EffectParams {
    /** Multiplies one feature's weight for everyone, for example "ignore missed calls" is missedCall x 0. */
    @Serializable
    @SerialName("GLOBAL_WEIGHT")
    data class GlobalWeight(val feature: String, val multiplier: Double) : EffectParams

    /** Adds [delta] points to one contact, optionally only on a weekday and/or part of the day. */
    @Serializable
    @SerialName("CONTACT_BOOST")
    data class ContactBoost(val contactKey: String, val delta: Double, val weekday: String? = null, val part: String? = null) : EffectParams

    /** The user wants to talk to the contact every [everyDays] days; [strength] 0..1 scales how much it matters. */
    @Serializable
    @SerialName("CADENCE")
    data class Cadence(val contactKey: String, val everyDays: Int, val strength: Double) : EffectParams

    /** Adds [boost] points to one contact (or everyone when [contactKey] is null) in the given weekdays and/or parts of the day. */
    @Serializable
    @SerialName("TIME_WINDOW")
    data class TimeWindow(val contactKey: String?, val weekdays: List<String>, val parts: List<String>, val boost: Double) : EffectParams
}

/** An effect created from an instruction. It can be switched off or deleted without touching the instruction. */
data class CompiledEffect(
    val id: Long,
    val sourceRuleId: Long,
    val params: EffectParams,
    val enabled: Boolean = true,
)

object EffectCodec {
    private val json = Json { classDiscriminator = "kind" }
    fun encode(params: EffectParams): String = json.encodeToString(EffectParams.serializer(), params)
    fun decode(text: String): EffectParams = json.decodeFromString(EffectParams.serializer(), text)
}

/** Plain-language lines for effects, for the Rules page and the score breakdown. */
object EffectText {
    fun featureLabel(id: String): String = when (id) {
        FeatureIds.FREQUENCY -> "call frequency"
        FeatureIds.WHATSAPP -> "WhatsApp"
        FeatureIds.WEEKDAY_HABIT -> "weekday habit"
        FeatureIds.TIME_OF_DAY_HABIT -> "time-of-day habit"
        FeatureIds.MISSED_CALL -> "missed-call"
        FeatureIds.CADENCE_DUE -> "cadence"
        FeatureIds.INITIATION -> "initiation"
        else -> id
    }

    fun describe(params: EffectParams, nameOf: (String) -> String): String = when (params) {
        is EffectParams.GlobalWeight -> "${featureLabel(params.feature).replaceFirstChar { it.uppercase() }} weight × ${trim(params.multiplier)}"
        is EffectParams.ContactBoost -> buildString {
            append(if (params.delta >= 0) "Boost " else "Lower ").append(nameOf(params.contactKey)).append(' ').append(signed(params.delta))
            condition(params.weekday?.let(::parseWeekday), params.part?.let(PartOfDay::parse))?.let { append(" ").append(it) }
        }
        is EffectParams.Cadence -> "Talk to ${nameOf(params.contactKey)} every ${params.everyDays} days (strength ${trim(params.strength)})"
        is EffectParams.TimeWindow -> buildString {
            append("Time window: ").append(params.contactKey?.let(nameOf) ?: "everyone").append(", ")
            append(
                listOfNotNull(
                    params.weekdays.mapNotNull(::parseWeekday).takeIf { it.isNotEmpty() }?.joinToString("/") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) },
                    params.parts.mapNotNull(PartOfDay::parse).takeIf { it.isNotEmpty() }?.joinToString("/") { it.name.lowercase() },
                ).ifEmpty { listOf("any time") }.joinToString(" "),
            )
            append(", ").append(signed(params.boost))
        }
    }

    private fun condition(day: java.time.DayOfWeek?, part: PartOfDay?): String? =
        listOfNotNull(day?.let { "on ${it.name.lowercase().replaceFirstChar(Char::uppercase)}s" }, part?.let { "in the ${it.name.lowercase()}" })
            .takeIf { it.isNotEmpty() }?.joinToString(" ")

    private fun signed(v: Double) = (if (v >= 0) "+" else "") + trim(v)
    private fun trim(v: Double) = if (v == v.toInt().toDouble()) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
