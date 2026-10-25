package com.smartdialer.llm

import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.data.WhatsAppEventStore
import com.smartdialer.data.WhatsAppSignals
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SkipReason { AI_OFF, NOT_ACKNOWLEDGED, NO_CONTACTS, NO_CANDIDATES, DAILY_CAP, QUEUED, BUSY, HAS_CACHE, UNCHANGED }

sealed interface RefreshOutcome {
    data object Updated : RefreshOutcome
    class Skipped(val reason: SkipReason) : RefreshOutcome
    class Failed(val message: String) : RefreshOutcome
}

/**
 * Refreshes the cached ranking with the LLM. A refresh never throws and never touches the UI thread; a
 * failure keeps the last good ranking, is recorded, and is retried later.
 *
 * The caller refreshes the local data (contacts and call log) first; this reads what they hold.
 */
class RankingService(
    private val llm: LlmClient,
    private val settings: AiSettings,
    private val cache: RankingCacheStore,
    private val exchanges: LlmExchangeStore,
    private val rules: RulesRepository,
    private val contacts: ContactsRepository,
    private val local: SuggestionsRepository,
    private val classifier: RuleClassifier?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val whatsApp: WhatsAppEventStore? = null,
    private val whatsAppEnabled: () -> Boolean = { false },
) {
    private val gate = RefreshGate()
    private val _isRefreshing = MutableStateFlow(false)
    private var lastInstructionSignature: String? = null

    /** True only while the ranking call itself is running (the "updating suggestions" indicator). */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()

    /**
     * Runs a ranking refresh for [trigger]. Every refresh counts against the daily cap ([RefreshQuota]). Never in
     * parallel: while one runs, a non-manual trigger queues exactly one follow-up and a manual one is ignored.
     */
    suspend fun refresh(trigger: RefreshTrigger = RefreshTrigger.MANUAL): RefreshOutcome {
        val s = settings.state.value
        if (!s.enabled) return RefreshOutcome.Skipped(SkipReason.AI_OFF)
        if (!s.disclosureAcknowledged) return RefreshOutcome.Skipped(SkipReason.NOT_ACKNOWLEDGED)
        if (!RefreshQuota.canStart(s, today())) return RefreshOutcome.Skipped(SkipReason.DAILY_CAP)
        when (gate.tryStart(trigger)) {
            RefreshGate.Start.IGNORED -> return RefreshOutcome.Skipped(SkipReason.BUSY)
            RefreshGate.Start.QUEUED -> return RefreshOutcome.Skipped(SkipReason.QUEUED)
            RefreshGate.Start.STARTED -> Unit
        }
        var outcome: RefreshOutcome
        try {
            outcome = runOnce(trigger)
            // A trigger that arrived meanwhile runs now, with fresh inputs, still within the cap.
            while (gate.nextOrFinish()) {
                outcome = if (RefreshQuota.canStart(settings.state.value, today())) {
                    runOnce(RefreshTrigger.INSTRUCTION)
                } else {
                    RefreshOutcome.Skipped(SkipReason.DAILY_CAP)
                }
            }
        } catch (e: Throwable) {
            gate.reset()
            throw e
        }
        return outcome
    }

    private suspend fun runOnce(trigger: RefreshTrigger): RefreshOutcome {
        // New instructions are classified first, so a new hard rule is a typed rule before the ranking runs.
        classifier?.classifyPending()

        val deviceContacts = contacts.contacts.value ?: return RefreshOutcome.Skipped(SkipReason.NO_CONTACTS)
        val now = clock()
        val inputs = CandidateSelector.select(
            contacts = deviceContacts.associate { it.contact.id to it.contact },
            activity = if (whatsApp != null && whatsAppEnabled()) {
                WhatsAppSignals.merge(local.activity.value, whatsApp.all(), now)
            } else {
                local.activity.value
            },
            freeText = rules.freeTextRules.first(),
            typed = rules.typedRules.first(),
            nowMillis = now,
            zone = zone(),
        )
        if (inputs.candidates.isEmpty()) return RefreshOutcome.Skipped(SkipReason.NO_CANDIDATES)

        val signature = RankingPrompt.instructionSignature(inputs)
        val unchanged = signature == lastInstructionSignature
        lastInstructionSignature = signature
        // Hard-rule changes and other edits that leave the LLM input alone need no call.
        if (trigger == RefreshTrigger.INSTRUCTION && unchanged) return RefreshOutcome.Skipped(SkipReason.UNCHANGED)
        if (trigger == RefreshTrigger.INITIAL && cache.load() != null) return RefreshOutcome.Skipped(SkipReason.HAS_CACHE)

        return rank(inputs, RankingPrompt.inputsHash(inputs))
    }

    private suspend fun rank(fullInputs: RankingInputs, hash: String): RefreshOutcome {
        val (inputs, leftOut) = RankingPrompt.fitToSize(fullInputs)
        val limitNotes = buildList {
            if (leftOut > 0) add("Limit reached: $leftOut contact(s) were left out because the request was too large.")
            if (inputs.unusedInstructions > 0) {
                add("Limit reached: only the ${InstructionLimits.MAX_PER_REQUEST} most recent instructions are sent; ${inputs.unusedInstructions} older one(s) are kept but not used.")
            }
        }
        settings.incrementRefreshCount(RefreshQuota.dayKey(today()))
        _isRefreshing.value = true
        val result = try {
            llm.complete(RankingPrompt.request(inputs))
        } finally {
            _isRefreshing.value = false
        }
        val now = clock()

        if (result is LlmResult.Failure) {
            exchanges.save(LlmExchange(RankingPrompt.PURPOSE, now, "Failed: ${result.kind.describe()}", result.requestBody, result.responseBody, listOf(result.message)))
            return fail(now, result.kind.describe())
        }
        result as LlmResult.Success

        val keys = inputs.candidates.map { it.contact.id }
        val parse = RankingParser.parse(result.text, keys.toSet())
        if (parse !is RankingParse.Valid) {
            parse as RankingParse.Unusable
            exchanges.save(LlmExchange(RankingPrompt.PURPOSE, now, "Unusable answer", result.requestBody, result.responseBody, listOf(parse.reason) + parse.issues))
            return fail(now, "The answer could not be used.")
        }

        val fallback = local.suggestions.value
        val entries = RankingMerge.merge(
            parsed = parse.entries,
            candidateKeys = keys,
            fallbackOrder = fallback.map { it.contact.id },
            fallbackReasons = fallback.associate { it.contact.id to it.reason },
            generatedAt = now,
        )
        cache.save(CachedRanking(entries, hash, Prompts.RANKING_VERSION, LlmConfig.MODEL, now))
        exchanges.save(LlmExchange(RankingPrompt.PURPOSE, now, "Ranked ${parse.entries.size} of ${keys.size} contacts", result.requestBody, result.responseBody, limitNotes + parse.issues))
        settings.recordSuccess(now)
        return RefreshOutcome.Updated
    }

    private fun fail(now: Long, message: String): RefreshOutcome {
        settings.recordFailure(now, message)
        return RefreshOutcome.Failed(message)
    }
}
