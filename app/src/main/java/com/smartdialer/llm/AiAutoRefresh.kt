package com.smartdialer.llm

import com.smartdialer.data.EngineMode
import com.smartdialer.data.EngineSettings
import com.smartdialer.data.LocalRanking
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reacts right away (no delay) when an instruction is added, edited or deleted. In Local mode that compiles the
 * instruction (the only LLM call) and the local engine updates itself from the new effects. In AI ranking mode the
 * ranking is refreshed (it compiles first). Hard rules need no call: enforcement applies them at once. If a refresh is
 * already running, one follow-up is queued.
 */
class AiAutoRefresh(
    scope: CoroutineScope,
    rules: RulesRepository,
    private val settings: AiSettings,
    private val service: RankingService,
    private val classifier: RuleClassifier,
    private val engine: EngineSettings,
    private val localRanking: LocalRanking,
) {
    init {
        // Turning AI on (or accepting the disclosure): compile what is waiting, or in AI mode do the first ranking.
        scope.launch {
            settings.state.map { it.canCallLlm }.distinctUntilChanged().collect {
                if (it) {
                    if (engine.mode.value == EngineMode.LOCAL) classifier.classifyPending() else service.refresh(RefreshTrigger.INITIAL)
                }
            }
        }
        scope.launch {
            rules.freeTextRules
                .map { list -> list.map { listOf(it.id, it.text, it.status, it.contactKey) } }
                .distinctUntilChanged()
                .drop(1) // the rules as they were when the app started are not a change
                .collect {
                    if (!settings.state.value.canCallLlm) return@collect
                    if (engine.mode.value == EngineMode.LOCAL) {
                        classifier.classifyPending()
                        localRanking.recompute()
                    } else {
                        service.refresh(RefreshTrigger.INSTRUCTION)
                    }
                }
        }
    }
}
