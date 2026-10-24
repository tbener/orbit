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
import kotlinx.coroutines.sync.Mutex
import java.time.ZoneId

enum class SkipReason { AI_OFF, NOT_ACKNOWLEDGED, NO_CONTACTS, NO_CANDIDATES, FRESH, BACKING_OFF, BUSY }

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
    private val mutex = Mutex()
    private val _isRefreshing = MutableStateFlow(false)

    /** True only while the ranking call itself is running (the "updating suggestions" indicator). */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun refresh(force: Boolean = false): RefreshOutcome {
        val gate = settings.state.value
        if (!gate.enabled) return RefreshOutcome.Skipped(SkipReason.AI_OFF)
        if (!gate.disclosureAcknowledged) return RefreshOutcome.Skipped(SkipReason.NOT_ACKNOWLEDGED)
        if (!mutex.tryLock()) return RefreshOutcome.Skipped(SkipReason.BUSY)
        try {
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

            val hash = RankingPrompt.inputsHash(inputs)
            val decision = RankingFreshness.decide(
                cached = cache.load(),
                currentHash = hash,
                promptVersion = Prompts.RANKING_VERSION,
                model = LlmConfig.MODEL,
                nowMillis = now,
                lastFailureAtMillis = settings.state.value.lastFailureAt,
                force = force,
            )
            when (decision) {
                RankingFreshness.Decision.FRESH -> return RefreshOutcome.Skipped(SkipReason.FRESH)
                RankingFreshness.Decision.BACKING_OFF -> return RefreshOutcome.Skipped(SkipReason.BACKING_OFF)
                RankingFreshness.Decision.REFRESH -> Unit
            }
            return rank(inputs, hash)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun rank(inputs: RankingInputs, hash: String): RefreshOutcome {
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
        exchanges.save(LlmExchange(RankingPrompt.PURPOSE, now, "Ranked ${parse.entries.size} of ${keys.size} contacts", result.requestBody, result.responseBody, parse.issues))
        settings.recordSuccess(now)
        return RefreshOutcome.Updated
    }

    private fun fail(now: Long, message: String): RefreshOutcome {
        settings.recordFailure(now, message)
        return RefreshOutcome.Failed(message)
    }
}
