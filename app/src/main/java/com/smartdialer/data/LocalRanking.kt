package com.smartdialer.data

import android.content.Context
import com.smartdialer.llm.AiSettings
import com.smartdialer.ranking.CompiledEffect
import com.smartdialer.ranking.FeatureIds
import com.smartdialer.ranking.LocalReasons
import com.smartdialer.ranking.ScoredContact
import com.smartdialer.ranking.ScoringConfig
import com.smartdialer.ranking.ScoringEngine
import com.smartdialer.ranking.ScoringInput
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/** Which engine decides the order on the home screen. */
enum class EngineMode(val label: String) {
    /** The local scoring engine; no LLM call to rank. The default. */
    LOCAL("Local"),

    /** The earlier path: the LLM ranks, and the result is cached. */
    AI("AI ranking"),
}

/** The engine choice and the tuned scoring values, kept on the phone. */
class EngineSettings(private val prefs: android.content.SharedPreferences?) {
    constructor(context: Context) : this(context.getSharedPreferences("orbit_engine", Context.MODE_PRIVATE))

    private val _mode = MutableStateFlow(EngineMode.entries.firstOrNull { it.name == prefs?.getString("mode", null) } ?: EngineMode.LOCAL)
    val mode: StateFlow<EngineMode> = _mode.asStateFlow()

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<ScoringConfig> = _config.asStateFlow()

    fun setMode(mode: EngineMode) {
        prefs?.edit()?.putString("mode", mode.name)?.apply()
        _mode.value = mode
    }

    fun setConfig(config: ScoringConfig) {
        prefs?.edit()?.apply {
            FeatureIds.all.forEach { putFloat("w_$it", config.weight(it).toFloat()) }
            putFloat("missedDayAfter", config.missedDayAfterFactor.toFloat())
            putInt("freq30", config.frequencyFull30d)
            putInt("freq90", config.frequencyFull90d)
            putInt("waDays", config.whatsAppRecentDays)
            putFloat("minBase", config.minBaseForTop.toFloat())
        }?.apply()
        _config.value = config
    }

    fun resetConfig() {
        prefs?.edit()?.apply { prefs.all.keys.filter { it != "mode" }.forEach(::remove) }?.apply()
        _config.value = ScoringConfig.DEFAULT
    }

    private fun readConfig(): ScoringConfig {
        val d = ScoringConfig.DEFAULT
        if (prefs == null) return d
        return ScoringConfig(
            weights = FeatureIds.all.associateWith { prefs.getFloat("w_$it", d.weight(it).toFloat()).toDouble() },
            missedDayAfterFactor = prefs.getFloat("missedDayAfter", d.missedDayAfterFactor.toFloat()).toDouble(),
            frequencyFull30d = prefs.getInt("freq30", d.frequencyFull30d),
            frequencyFull90d = prefs.getInt("freq90", d.frequencyFull90d),
            whatsAppRecentDays = prefs.getInt("waDays", d.whatsAppRecentDays),
            minBaseForTop = prefs.getFloat("minBase", d.minBaseForTop.toFloat()).toDouble(),
        )
    }
}

/** What the local engine saw and decided at one moment. */
class LocalRankingState(
    val inputs: List<ScoringInput>,
    val effects: List<CompiledEffect>,
    val config: ScoringConfig,
    val nowMillis: Long,
    val ranked: List<ScoredContact>,
)

/**
 * Runs the local scoring engine whenever its inputs change (call log, contacts, effects, weights, WhatsApp) and when
 * [recompute] is called (app open, the schedule, a swipe). It makes no network call. It runs off the main thread.
 */
class LocalRanking(
    local: SuggestionsRepository,
    contacts: ContactsRepository,
    rules: RulesRepository,
    engine: EngineSettings,
    whatsAppEvents: Flow<List<WhatsAppEvent>>,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val tick = MutableStateFlow(0)

    val state: StateFlow<LocalRankingState?> = combine(
        combine(local.activity, whatsAppEvents) { activity, events -> activity to events },
        contacts.contacts,
        rules.effects,
        engine.config,
        tick,
    ) { (activity, events), deviceContacts, effects, config, _ ->
        val now = clock()
        val list = deviceContacts?.map { it.contact }.orEmpty()
        val names = list.associate { it.id to it.name }
        val merged = WhatsAppSignals.merge(activity, events, now)
        val extra = effects.mapNotNull { e ->
            when (val p = e.params) {
                is com.smartdialer.ranking.EffectParams.ContactBoost -> p.contactKey
                is com.smartdialer.ranking.EffectParams.Cadence -> p.contactKey
                else -> null
            }
        }.toSet()
        val inputs = ScoringInputs.build(list, merged, whatsAppEnabled = events.isNotEmpty(), nowMillis = now, zone = zone(), extraKeys = extra)
        LocalRankingState(inputs, effects, config, now, ScoringEngine.score(inputs, effects, config, now, zone(), nameOf = { names[it] ?: it }))
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    /** Scores again now (the time of day and weekday may have changed). */
    fun recompute() {
        tick.value++
    }

    /** The order the engine would give with [config] on the same data (for the tuning screen). */
    fun preview(config: ScoringConfig): List<ScoredContact> {
        val s = state.value ?: return emptyList()
        val names = s.inputs.associate { it.contactKey to it.name }
        return ScoringEngine.score(s.inputs, s.effects, config, s.nowMillis, zone(), nameOf = { names[it] ?: it })
    }
}

/** Builds home-screen suggestions from the local engine's result. Pure. */
object LocalSuggestions {
    fun build(fallback: List<Suggestion>, scored: List<ScoredContact>, contacts: Map<String, Contact>?): List<Suggestion> {
        val byId = fallback.associateBy { it.contact.id }
        val ranked = scored.mapNotNull { s ->
            val known = byId[s.contactKey]
            val contact = known?.contact ?: contacts?.get(s.contactKey) ?: return@mapNotNull null
            Suggestion(
                contact = contact,
                subtitle = known?.subtitle,
                badge = known?.badge,
                reason = LocalReasons.reason(s).ifBlank { known?.reason ?: "" },
            )
        }
        val ids = ranked.map { it.contact.id }.toSet()
        return ranked + fallback.filter { it.contact.id !in ids }
    }
}
