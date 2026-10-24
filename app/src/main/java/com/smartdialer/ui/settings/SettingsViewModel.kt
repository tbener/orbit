package com.smartdialer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.llm.AiSettings
import com.smartdialer.llm.LlmExchange
import com.smartdialer.llm.LlmExchangeStore
import com.smartdialer.llm.RankingCacheStore
import com.smartdialer.llm.RankingPrompt
import com.smartdialer.llm.ClassificationPrompt
import com.smartdialer.llm.RankingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val enabled: Boolean = true,
    val acknowledged: Boolean = false,
    val refreshing: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    /** True when the most recent attempt failed (the last good ranking is still shown). */
    val failing: Boolean = false,
    val lastStatus: String? = null,
    val promptVersion: String? = null,
    val model: String? = null,
    val apiKeyMissing: Boolean = false,
)

data class InspectorUiState(
    val ranking: LlmExchange? = null,
    val classification: LlmExchange? = null,
)

class SettingsViewModel(
    private val settings: AiSettings,
    private val suggestions: AiSuggestionsRepository,
    cache: RankingCacheStore,
    service: RankingService,
    exchanges: LlmExchangeStore,
    apiKeyMissing: Boolean,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(settings.state, cache.ranking, service.isRefreshing) { s, ranking, refreshing ->
        SettingsUiState(
            enabled = s.enabled,
            acknowledged = s.disclosureAcknowledged,
            refreshing = refreshing,
            lastSuccessAt = s.lastSuccessAt,
            lastAttemptAt = s.lastAttemptAt,
            failing = s.lastFailureAt != null,
            lastStatus = s.lastStatus,
            promptVersion = ranking?.promptVersion,
            model = ranking?.model,
            apiKeyMissing = apiKeyMissing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(apiKeyMissing = apiKeyMissing))

    val inspector: StateFlow<InspectorUiState> = combine(
        exchanges.observe(RankingPrompt.PURPOSE),
        exchanges.observe(ClassificationPrompt.PURPOSE),
    ) { ranking, classification -> InspectorUiState(ranking, classification) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InspectorUiState())

    /** Turning on before the disclosure was acknowledged is handled by the screen, which asks first. */
    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    fun acceptDisclosure() {
        settings.acknowledgeDisclosure()
        settings.setEnabled(true)
    }

    fun refreshNow() {
        viewModelScope.launch { suggestions.refreshNow() }
    }
}
