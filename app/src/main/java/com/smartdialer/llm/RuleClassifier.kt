package com.smartdialer.llm

import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.MAX_SUGGESTIONS
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ClassifyOutcome { DONE, NOTHING_TO_DO, AI_OFF, NO_CONTACTS, FAILED }

/**
 * Classifies new free-text rules as hard or soft. Hard rules become typed rules (linked both ways); anything
 * unclear is marked for review and never guessed. Nothing it decides is hidden: every rule keeps the model's
 * one-sentence explanation, and the Rules page lets the user override it.
 */
class RuleClassifier(
    private val llm: LlmClient,
    private val settings: AiSettings,
    private val rules: RulesRepository,
    private val contacts: ContactsRepository,
    private val exchanges: LlmExchangeStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun classifyPending(): ClassifyOutcome = mutex.withLock {
        if (!settings.state.value.canCallLlm) return ClassifyOutcome.AI_OFF
        val pending = rules.freeTextRules.first().filter { it.status == FreeTextStatus.UNCLASSIFIED }
        if (pending.isEmpty()) return ClassifyOutcome.NOTHING_TO_DO
        val deviceContacts = contacts.contacts.value?.map { it.contact } ?: return ClassifyOutcome.NO_CONTACTS
        val names = deviceContacts.associate { it.id to it.name }

        var failed = false
        for (batch in pending.chunked(BATCH_SIZE)) {
            if (!classifyBatch(batch, deviceContacts, names)) failed = true
        }
        return if (failed) ClassifyOutcome.FAILED else ClassifyOutcome.DONE
    }

    private suspend fun classifyBatch(
        batch: List<FreeTextRule>,
        deviceContacts: List<com.smartdialer.data.Contact>,
        names: Map<String, String>,
    ): Boolean {
        val request = ClassificationPrompt.request(
            batch.map { RuleToClassify(it.id, it.text, it.contactKey?.let(names::get)) },
            MAX_SUGGESTIONS,
        )
        val result = llm.complete(request)
        val now = clock()
        if (result is LlmResult.Failure) {
            exchanges.save(LlmExchange(ClassificationPrompt.PURPOSE, now, "Failed: ${result.kind.describe()}", result.requestBody, result.responseBody, listOf(result.message)))
            return false
        }
        result as LlmResult.Success
        val parse = ClassificationParser.parse(result.text, batch.map { it.id }.toSet())
        if (parse !is ClassificationParse.Valid) {
            val reason = (parse as ClassificationParse.Unusable).reason
            exchanges.save(LlmExchange(ClassificationPrompt.PURPOSE, now, "Unusable answer", result.requestBody, result.responseBody, listOf(reason)))
            return false
        }

        val errors = parse.issues.toMutableList()
        for (item in parse.items) {
            // The rule may have been edited or deleted while the call was running; only apply to what is still waiting.
            val current = rules.freeTextRules.first().firstOrNull { it.id == item.ruleId }
            val original = batch.first { it.id == item.ruleId }
            if (current == null || current.status != FreeTextStatus.UNCLASSIFIED || current.text != original.text) {
                errors += "Rule ${item.ruleId} changed while it was being classified; skipped."
                continue
            }
            when (val decision = ClassificationResolver.resolve(current, item, deviceContacts, MAX_SUGGESTIONS)) {
                is ClassificationDecision.Soft ->
                    rules.setClassification(current.id, FreeTextStatus.SOFT, decision.explanation)
                is ClassificationDecision.Hard ->
                    rules.addTypedRule(decision.params, sourceFreeTextRuleId = current.id, explanation = decision.explanation)
                is ClassificationDecision.NeedsReview ->
                    rules.setClassification(current.id, FreeTextStatus.NEEDS_REVIEW, decision.explanation, decision.proposedType, decision.proposedSlot)
            }
        }
        exchanges.save(LlmExchange(ClassificationPrompt.PURPOSE, now, "Classified ${parse.items.size} of ${batch.size} rule(s)", result.requestBody, result.responseBody, errors))
        return true
    }

    private companion object {
        const val BATCH_SIZE = 25
    }
}
