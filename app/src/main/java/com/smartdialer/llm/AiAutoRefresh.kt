package com.smartdialer.llm

import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Refreshes in the background shortly after the rules or instructions change (and when the AI is switched
 * on), so new instructions are classified and a new rule is reflected without waiting for the next open. The
 * refresh itself decides, by the inputs hash, whether anything needs to be asked of the model.
 */
@OptIn(FlowPreview::class)
class AiAutoRefresh(
    scope: CoroutineScope,
    rules: RulesRepository,
    private val settings: AiSettings,
    private val service: RankingService,
    debounceMillis: Long = 2_500,
) {
    init {
        scope.launch {
            combine(
                rules.freeTextRules,
                rules.typedRules,
                settings.state.map { it.canCallLlm }.distinctUntilChanged(),
            ) { _, _, _ -> Unit }
                .debounce(debounceMillis)
                .collect { if (settings.state.value.canCallLlm) service.refresh(force = false) }
        }
    }
}
