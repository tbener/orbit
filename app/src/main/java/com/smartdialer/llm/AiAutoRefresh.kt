package com.smartdialer.llm

import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Refreshes right away (no delay) when an instruction is added, edited or deleted, and classifies it first.
 * The service decides whether the LLM input really changed: hard rules (pins and hides) are applied by the
 * enforcement layer at once and need no call. If a refresh is already running, one follow-up is queued.
 */
class AiAutoRefresh(
    scope: CoroutineScope,
    rules: RulesRepository,
    private val settings: AiSettings,
    private val service: RankingService,
) {
    init {
        // Turning AI on (or accepting the disclosure) with no cached ranking yet: one initial refresh.
        scope.launch {
            settings.state.map { it.canCallLlm }.distinctUntilChanged().collect { if (it) service.refresh(RefreshTrigger.INITIAL) }
        }
        scope.launch {
            rules.freeTextRules
                .map { list -> list.map { listOf(it.id, it.text, it.status, it.contactKey) } }
                .distinctUntilChanged()
                .drop(1) // the rules as they were when the app started are not a change
                .collect { if (settings.state.value.canCallLlm) service.refresh(RefreshTrigger.INSTRUCTION) }
        }
    }
}
