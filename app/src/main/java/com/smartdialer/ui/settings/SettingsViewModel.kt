package com.smartdialer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.LayoutMode
import com.smartdialer.data.UiSettings
import com.smartdialer.data.WhatsAppEventStore
import com.smartdialer.llm.RefreshQuota
import com.smartdialer.data.WhatsAppSettings
import com.smartdialer.llm.AiSettings
import kotlinx.coroutines.flow.MutableStateFlow
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
    val whatsAppEnabled: Boolean = true,
    /** Notification access is granted in the system settings. */
    val whatsAppGranted: Boolean = false,
    val whatsAppEventCount: Int = 0,
    val layoutMode: LayoutMode = LayoutMode.Grid3,
    /** Ranking refreshes started today, and the daily cap. */
    val refreshCountToday: Int = 0,
    val refreshCap: Int = RefreshQuota.DAILY_CAP,
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
    private val whatsAppSettings: WhatsAppSettings,
    private val whatsAppStore: WhatsAppEventStore,
    private val uiSettings: UiSettings,
) : ViewModel() {

    private val whatsAppGranted = MutableStateFlow(false)

    /** Called when the app returns to the foreground, since notification access is changed in system settings. */
    fun onAccessChecked(granted: Boolean) {
        whatsAppGranted.value = granted
    }

    private val whatsAppState = combine(whatsAppSettings.state, whatsAppGranted, whatsAppStore.events) { s, granted, events ->
        Triple(s.enabled, granted, events.size)
    }

    val state: StateFlow<SettingsUiState> = combine(
        combine(settings.state, cache.ranking, service.isRefreshing) { s, ranking, refreshing -> Triple(s, ranking, refreshing) },
        whatsAppState,
        uiSettings.layoutMode,
    ) { (s, ranking, refreshing), (waEnabled, waGranted, waCount), layout ->
        SettingsUiState(
            layoutMode = layout,
            refreshCountToday = RefreshQuota.countToday(s, java.time.LocalDate.now()),
            whatsAppEnabled = waEnabled,
            whatsAppGranted = waGranted,
            whatsAppEventCount = waCount,
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

    fun setLayoutMode(mode: LayoutMode) = uiSettings.setLayoutMode(mode)

    fun setWhatsAppEnabled(enabled: Boolean) = whatsAppSettings.setEnabled(enabled)

    fun deleteWhatsAppData() {
        viewModelScope.launch { whatsAppStore.deleteAll() }
    }

    fun refreshNow() {
        viewModelScope.launch { suggestions.refreshManual() }
    }
}
