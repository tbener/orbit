package com.smartdialer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartdialer.data.EngineSettings
import com.smartdialer.data.LocalRanking
import com.smartdialer.llm.RankingService
import com.smartdialer.llm.SkipReason
import com.smartdialer.ranking.ScoredContact
import com.smartdialer.ranking.ScoringConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareUiState(
    /** The local engine's order, with the contribution breakdown. */
    val local: List<ScoredContact> = emptyList(),
    /** The AI's order (contact ids), after a manual run; not saved anywhere. */
    val ai: List<String>? = null,
    val names: Map<String, String> = emptyMap(),
    val running: Boolean = false,
    val status: String? = null,
)

/** "Compare engines": both orders on the same data. The AI run is manual, counts toward the daily cap and is not cached. */
class CompareViewModel(
    localRanking: LocalRanking,
    private val service: RankingService,
) : ViewModel() {

    private val ai = MutableStateFlow<List<String>?>(null)
    private val running = MutableStateFlow(false)
    private val status = MutableStateFlow<String?>(null)

    val state: StateFlow<CompareUiState> = combine(localRanking.state, ai, running, status) { local, aiOrder, isRunning, message ->
        CompareUiState(
            local = local?.ranked.orEmpty(),
            ai = aiOrder,
            names = local?.inputs?.associate { it.contactKey to it.name }.orEmpty(),
            running = isRunning,
            status = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    fun runAi() {
        if (running.value) return
        viewModelScope.launch {
            running.value = true
            status.value = null
            when (val result = service.compare()) {
                is RankingService.CompareResult.Ranked -> { ai.value = result.order; status.value = "AI ranking done (not saved)." }
                is RankingService.CompareResult.Failed -> status.value = "AI ranking failed: ${result.message}"
                is RankingService.CompareResult.Skipped -> status.value = when (result.reason) {
                    SkipReason.DAILY_CAP -> "Daily AI limit reached."
                    SkipReason.AI_OFF, SkipReason.NOT_ACKNOWLEDGED -> "Turn AI suggestions on in Settings first."
                    SkipReason.BUSY -> "Another AI call is running."
                    else -> "Nothing to rank yet."
                }
            }
            running.value = false
        }
    }
}

data class TuningUiState(
    val config: ScoringConfig = ScoringConfig.DEFAULT,
    /** The top of the order the current values give, live. */
    val preview: List<ScoredContact> = emptyList(),
)

/** The weight tuning screen: sliders with a live preview of the top 15. Values are saved as they move. */
class TuningViewModel(
    private val engine: EngineSettings,
    private val localRanking: LocalRanking,
) : ViewModel() {

    private val ticks = MutableStateFlow(0)

    val state: StateFlow<TuningUiState> = combine(engine.config, localRanking.state, ticks) { config, _, _ ->
        TuningUiState(config, localRanking.preview(config).take(PREVIEW_SIZE))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TuningUiState())

    fun setWeight(featureId: String, value: Double) {
        val c = engine.config.value
        engine.setConfig(c.copy(weights = c.weights + (featureId to value)))
        ticks.update { it + 1 }
    }

    fun update(change: (ScoringConfig) -> ScoringConfig) {
        engine.setConfig(change(engine.config.value))
        ticks.update { it + 1 }
    }

    fun reset() {
        engine.resetConfig()
        ticks.update { it + 1 }
    }

    private companion object {
        const val PREVIEW_SIZE = 15
    }
}
