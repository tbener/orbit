package com.smartdialer.llm

import com.smartdialer.data.FallbackRanker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

/**
 * Builds the ranking request. **Privacy:** only the opaque contact key, the display name, call-log
 * signals (days, counts) and the user's own instructions are sent. Never phone numbers, emails, photos, or
 * anything else from the contact.
 */
object RankingPrompt {

    fun request(inputs: RankingInputs): LlmRequest = LlmRequest(
        purpose = PURPOSE,
        system = Prompts.ranking,
        user = userMessage(inputs),
        schema = schema,
        maxTokens = LlmConfig.RANKING_MAX_TOKENS,
    )

    const val PURPOSE = "ranking"

    private val compact = Json { encodeDefaults = true }

    /**
     * Keeps the request under [InstructionLimits.MAX_PAYLOAD_CHARS] by leaving out contacts from the end of the
     * list. Returns the inputs to send and how many contacts were left out.
     */
    fun fitToSize(inputs: RankingInputs): Pair<RankingInputs, Int> {
        var kept = inputs.candidates
        while (kept.size > 1 && userMessage(copyWith(inputs, kept)).length > InstructionLimits.MAX_PAYLOAD_CHARS) {
            kept = kept.dropLast(maxOf(1, kept.size / 20))
        }
        return copyWith(inputs, kept) to (inputs.candidates.size - kept.size)
    }

    private fun copyWith(i: RankingInputs, candidates: List<RankingCandidate>) = RankingInputs(
        i.nowMillis, i.zone, i.generalInstructions, i.pins.filter { p -> candidates.any { it.contact.id == p.contactKey } },
        i.hiddenCount, candidates, i.unusedInstructions,
    )

    fun userMessage(inputs: RankingInputs): String = compact.encodeToString(JsonObject.serializer(), payload(inputs))

    fun payload(inputs: RankingInputs): JsonObject {
        val now = Instant.ofEpochMilli(inputs.nowMillis).atZone(inputs.zone)
        return buildJsonObject {
            put(
                "now",
                buildJsonObject {
                    put("date", now.toLocalDate().toString())
                    put("weekday", now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    put("localTime", "%02d:%02d".format(now.hour, now.minute))
                    put("timeOfDay", timeOfDay(now.hour))
                    put("timezone", inputs.zone.id)
                },
            )
            put("generalInstructions", JsonArray(inputs.generalInstructions.map { JsonPrimitive(Sanitizer.cleanInstruction(it)) }))
            put(
                "hardRules",
                buildJsonArray {
                    inputs.pins.forEach {
                        add(
                            buildJsonObject {
                                put("contactKey", it.contactKey)
                                put("name", Sanitizer.cleanName(it.name))
                                put("slot", it.slot)
                            },
                        )
                    }
                },
            )
            put("hiddenContacts", inputs.hiddenCount)
            put(
                "candidates",
                buildJsonArray {
                    inputs.candidates.forEach { candidate ->
                        val s = candidate.signals
                        add(
                            buildJsonObject {
                                put("contactKey", candidate.contact.id)
                                put("name", Sanitizer.cleanName(candidate.contact.name))
                                put(
                                    "signals",
                                    buildJsonObject {
                                        put("lastSpokeDaysAgo", s.lastContactMillis?.let { daysAgo(it, inputs) }?.let(::JsonPrimitive) ?: JsonNull)
                                        put(
                                            "lastSpokeDate",
                                            s.lastContactMillis?.let { Instant.ofEpochMilli(it).atZone(inputs.zone).toLocalDate().toString() }
                                                ?.let(::JsonPrimitive) ?: JsonNull,
                                        )
                                        put("unreturnedMissedCalls", s.unreturnedMissedCalls)
                                        put("lastMissedDaysAgo", s.lastMissedMillis?.let { daysAgo(it, inputs) }?.let(::JsonPrimitive) ?: JsonNull)
                                        // Real conversations only: answered calls, not missed ones or outgoing calls that rang out.
                                        put("callsInHistoryWindow", s.talkCount)
                                        put("missedCallsInWindow", s.missedCount)
                                        put("unansweredOutgoingCallsInWindow", s.unansweredOutgoingCount)
                                        if (s.hasWhatsApp) {
                                            put(
                                                "whatsapp",
                                                buildJsonObject {
                                                    put("lastIncomingDaysAgo", s.lastWhatsAppIncomingAt?.let { daysAgo(it, inputs) }?.let(::JsonPrimitive) ?: JsonNull)
                                                    put("incomingNotifications7d", s.whatsAppIncoming7d)
                                                    put("incomingNotifications30d", s.whatsAppIncoming30d)
                                                    put("lastOutgoingFromOrbitDaysAgo", s.lastWhatsAppOutgoingFromOrbitAt?.let { daysAgo(it, inputs) }?.let(::JsonPrimitive) ?: JsonNull)
                                                },
                                            )
                                        }
                                    },
                                )
                                put("instructions", JsonArray(candidate.instructions.map { JsonPrimitive(Sanitizer.cleanInstruction(it)) }))
                            },
                        )
                    }
                },
            )
        }
    }

    private fun daysAgo(millis: Long, inputs: RankingInputs): Long = FallbackRanker.daysBetween(millis, inputs.nowMillis, inputs.zone)

    private fun timeOfDay(hour: Int): String = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }

    /**
     * What the ranking depends on, as a hash: the instructions, the pins and hidden contacts, each
     * candidate's key, name and raw call timestamps, the prompt version and the model. It deliberately
     * ignores the clock, so the hash only changes when something the user or the call log did changes it.
     */
    fun inputsHash(inputs: RankingInputs, promptVersion: String = Prompts.RANKING_VERSION, model: String = LlmConfig.MODEL): String {
        val canonical = buildString {
            append(promptVersion).append('\n').append(model).append('\n')
            // Editing the prompt file changes the result, so it changes the hash and the cache refreshes.
            append(Prompts.ranking.hashCode()).append('\n')
            append("general:").append(inputs.generalInstructions.sorted().joinToString("|")).append('\n')
            append("pins:").append(inputs.pins.sortedBy { it.contactKey }.joinToString("|") { "${it.contactKey}=${it.slot}" }).append('\n')
            append("hidden:").append(inputs.hiddenCount).append('\n')
            inputs.candidates.sortedBy { it.contact.id }.forEach { c ->
                val s = c.signals
                append(c.contact.id).append('~').append(c.contact.name).append('~')
                append(s.lastContactMillis).append('~').append(s.unreturnedMissedCalls).append('~')
                append(s.lastMissedMillis).append('~').append(s.callCount).append('~')
                append(s.talkCount).append('~').append(s.missedCount).append('~').append(s.unansweredOutgoingCount).append('~')
                append(s.lastWhatsAppIncomingAt).append('~').append(s.lastWhatsAppOutgoingFromOrbitAt).append('~')
                append(c.instructions.sorted().joinToString("|")).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** The structured-output schema. Constraints such as 0..100 are enforced when the answer is validated. */
    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "ranking",
                    buildJsonObject {
                        put("type", "array")
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("contactKey", buildJsonObject { put("type", "string") })
                                        put("score", buildJsonObject { put("type", "integer") })
                                        put(
                                            "tier",
                                            buildJsonObject {
                                                put("type", "string")
                                                put("enum", JsonArray(listOf("top", "high", "normal", "low").map(::JsonPrimitive)))
                                            },
                                        )
                                        put("reason", buildJsonObject { put("type", "string") })
                                    },
                                )
                                put("required", JsonArray(listOf("contactKey", "score", "tier", "reason").map(::JsonPrimitive)))
                                put("additionalProperties", false)
                            },
                        )
                    },
                )
            },
        )
        put("required", JsonArray(listOf(JsonPrimitive("ranking"))))
        put("additionalProperties", false)
    }
}
