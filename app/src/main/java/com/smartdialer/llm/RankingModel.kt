package com.smartdialer.llm

import com.smartdialer.data.Contact
import com.smartdialer.data.ContactActivity
import com.smartdialer.data.ContactSignals
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import java.time.ZoneId

enum class Tier {
    TOP, HIGH, NORMAL, LOW;

    companion object {
        /** Reads a tier case-insensitively; null when it is not one of the four. */
        fun parse(text: String?): Tier? = entries.firstOrNull { it.name.equals(text?.trim(), ignoreCase = true) }

        fun fromScore(score: Int): Tier = when {
            score >= 85 -> TOP
            score >= 60 -> HIGH
            score >= 30 -> NORMAL
            else -> LOW
        }
    }
}

enum class EntrySource { LLM, FALLBACK }

/** One contact's place in a ranking result, as cached. [rank] is 1 for the best. */
data class CachedEntry(
    val contactKey: String,
    val rank: Int,
    val score: Int,
    val tier: Tier,
    val reason: String,
    val source: EntrySource,
    val generatedAt: Long,
)

/** The latest ranking: the entries plus what they were made from. */
data class CachedRanking(
    val entries: List<CachedEntry>,
    /** Hash of everything the model was told (see [RankingPrompt.inputsHash]). */
    val inputsHash: String,
    val promptVersion: String,
    val model: String,
    val generatedAt: Long,
)

/** A contact the model is asked to rank, with what Orbit shares about them: key, name, call signals, instructions. */
data class RankingCandidate(
    val contact: Contact,
    val signals: ContactSignals,
    val instructions: List<String>,
)

/** A pin the user set. Sent only as context; code enforces it after the model answers. */
data class PinContext(val contactKey: String, val name: String, val slot: Int)

class RankingInputs(
    val nowMillis: Long,
    val zone: ZoneId,
    val generalInstructions: List<String>,
    val pins: List<PinContext>,
    val hiddenCount: Int,
    val candidates: List<RankingCandidate>,
)

/** Chooses who goes into the ranking call. Pure. */
object CandidateSelector {

    /**
     * Candidates are the contacts with recent call history plus any contact with an instruction or a pin,
     * capped at [max]. Contacts under a NEVER_SHOW rule are left out entirely. Contacts with instructions or
     * pins always make the cut; the rest are chosen by: unreturned missed calls first, then more history,
     * then more recent activity.
     */
    fun select(
        contacts: Map<String, Contact>,
        activity: Map<String, ContactActivity>,
        freeText: List<FreeTextRule>,
        typed: List<TypedRule>,
        nowMillis: Long,
        zone: ZoneId,
        max: Int = LlmConfig.MAX_CANDIDATES,
    ): RankingInputs {
        val hidden = typed.map { it.params }.filterIsInstance<TypedRuleParams.NeverShow>().map { it.contact.lookupKey }.toSet()
        val pins = typed.map { it.params }.filterIsInstance<TypedRuleParams.PinToSlot>()
            .filter { it.contact.lookupKey !in hidden && it.contact.lookupKey in contacts }
            .map { PinContext(it.contact.lookupKey, contacts.getValue(it.contact.lookupKey).name, it.slot) }

        // Instructions that code already enforces are not repeated to the model.
        val live = freeText.filter { it.status != FreeTextStatus.CONVERTED_TO_TYPED }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val general = live.filter { it.contactKey == null }.map { it.text }
        val byContact = live.filter { it.contactKey != null }.groupBy({ it.contactKey!! }, { it.text })

        val must = (byContact.keys + pins.map { it.contactKey })
            .filter { it in contacts && it !in hidden }
            .distinct()
            .sortedBy { contacts.getValue(it).name.lowercase() }
        val mustSet = must.toSet()

        val rest = activity.entries
            .filter { (key, value) -> key in contacts && key !in hidden && key !in mustSet && (value.signals.callCount > 0 || value.signals.hasWhatsApp) }
            .sortedWith(
                compareByDescending<Map.Entry<String, ContactActivity>> { it.value.signals.unreturnedMissedCalls > 0 }
                    .thenByDescending { it.value.signals.callCount }
                    .thenByDescending { maxOf(it.value.signals.lastContactMillis ?: 0L, it.value.signals.lastMissedMillis ?: 0L) }
                    .thenBy { contacts.getValue(it.key).name.lowercase() },
            )
            .map { it.key }

        val keys = (must + rest).take(max)
        val candidates = keys.map { key ->
            RankingCandidate(
                contact = contacts.getValue(key),
                signals = activity[key]?.signals ?: ContactSignals(null, 0, null, 0),
                instructions = byContact[key].orEmpty(),
            )
        }
        val kept = keys.toSet()
        return RankingInputs(
            nowMillis = nowMillis,
            zone = zone,
            generalInstructions = general,
            pins = pins.filter { it.contactKey in kept },
            hiddenCount = hidden.count { it in contacts },
            candidates = candidates,
        )
    }
}

/** Decides whether the cached ranking needs a refresh. Pure. */
object RankingFreshness {
    enum class Decision { REFRESH, FRESH, BACKING_OFF }

    /**
     * - [force] (the user asked) always refreshes.
     * - After a failed attempt, wait [LlmConfig.RETRY_AFTER_FAILURE_MILLIS] before trying again on its own.
     * - No cache, a changed inputs hash (new calls, changed rules or instructions), a different prompt version
     *   or model, or a cache older than [LlmConfig.STALE_AFTER_MILLIS] all refresh.
     */
    fun decide(
        cached: CachedRanking?,
        currentHash: String,
        promptVersion: String,
        model: String,
        nowMillis: Long,
        lastFailureAtMillis: Long?,
        force: Boolean,
    ): Decision {
        if (force) return Decision.REFRESH
        if (lastFailureAtMillis != null && nowMillis - lastFailureAtMillis < LlmConfig.RETRY_AFTER_FAILURE_MILLIS) {
            return Decision.BACKING_OFF
        }
        val stale = cached == null ||
            cached.inputsHash != currentHash ||
            cached.promptVersion != promptVersion ||
            cached.model != model ||
            nowMillis - cached.generatedAt > LlmConfig.STALE_AFTER_MILLIS
        return if (stale) Decision.REFRESH else Decision.FRESH
    }
}
