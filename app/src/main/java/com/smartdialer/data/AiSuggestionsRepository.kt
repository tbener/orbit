package com.smartdialer.data

import com.smartdialer.llm.AiSettings
import com.smartdialer.llm.CachedRanking
import com.smartdialer.llm.RankingCacheStore
import com.smartdialer.llm.RankingService
import com.smartdialer.llm.RefreshOutcome
import com.smartdialer.llm.RefreshTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The ranking the home screen starts from: the cached LLM ranking when AI suggestions are on and there is one,
 * otherwise the [FallbackRanker] list from [local]. Hard rules are applied afterwards by [SuggestionPipeline],
 * every time, so this never has to know about them.
 */
class AiSuggestionsRepository(
    private val local: SuggestionsRepository,
    cache: RankingCacheStore,
    settings: AiSettings,
    contacts: ContactsRepository,
    private val service: RankingService,
    scope: CoroutineScope,
    private val localRanking: LocalRanking,
    private val engine: EngineSettings,
) : SuggestionsRepository {

    override val suggestions: StateFlow<List<Suggestion>> = combine(
        combine(local.suggestions, cache.ranking, settings.state, contacts.contacts) { fallback, ranking, state, deviceContacts ->
            // The AI path: the cached LLM ranking when AI suggestions are on and there is one.
            SuggestionMerger.merge(
                fallback = fallback,
                ranking = ranking.takeIf { state.enabled },
                contacts = deviceContacts?.associate { it.contact.id to it.contact },
            ) to (fallback to deviceContacts)
        },
        engine.mode,
        localRanking.state,
    ) { (aiList, base), mode, localState ->
        val (fallback, deviceContacts) = base
        if (mode == EngineMode.LOCAL && localState != null) {
            LocalSuggestions.build(fallback, localState.ranked, deviceContacts?.associate { it.contact.id to it.contact })
        } else {
            aiList
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val activity = local.activity

    private val localRefreshing = MutableStateFlow(false)

    /** True while the LLM ranking call runs, or briefly while the local engine runs for a swipe. */
    override val isRefreshing: StateFlow<Boolean> =
        combine(service.isRefreshing, localRefreshing) { a, b -> a || b }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Reads contacts and the call log (so the fallback list is current at once) and runs the local engine. In AI
     * ranking mode the LLM ranking is only asked for when there is no cached ranking yet.
     */
    override suspend fun refresh() {
        local.refresh()
        localRanking.recompute()
        if (engine.mode.value == EngineMode.AI) service.refresh(RefreshTrigger.INITIAL)
    }

    /** The scheduled background refresh (morning and evening). Local mode makes no LLM call. */
    suspend fun refreshScheduled(): RefreshOutcome {
        local.refresh()
        localRanking.recompute()
        return if (engine.mode.value == EngineMode.AI) service.refresh(RefreshTrigger.SCHEDULED) else RefreshOutcome.Updated
    }

    /** Swipe down, or "Refresh now" in Settings. Local mode makes no LLM call; AI mode counts against the daily cap. */
    suspend fun refreshManual(): RefreshOutcome {
        if (engine.mode.value == EngineMode.AI) {
            local.refresh()
            localRanking.recompute()
            return service.refresh(RefreshTrigger.MANUAL)
        }
        localRefreshing.value = true
        try {
            local.refresh()
            localRanking.recompute()
            kotlinx.coroutines.delay(600) // long enough to see the indicator
        } finally {
            localRefreshing.value = false
        }
        return RefreshOutcome.Updated
    }
}

/** Merges the cached ranking into suggestions. Pure. */
object SuggestionMerger {

    /**
     * Without a ranking the [fallback] list is returned as it is. With one, cached contacts come in cached order,
     * showing the cached reason (or the fallback's, or a plain line when there is neither) while subtitles and
     * the missed-call badge always come from the call log. Contacts that are in the fallback but not yet in the
     * cache (new since the last refresh) follow at the end.
     */
    fun merge(fallback: List<Suggestion>, ranking: CachedRanking?, contacts: Map<String, Contact>?): List<Suggestion> {
        if (ranking == null || ranking.entries.isEmpty()) return fallback
        val fallbackById = fallback.associateBy { it.contact.id }
        val ranked = ranking.entries.sortedBy { it.rank }.mapNotNull { entry ->
            val known = fallbackById[entry.contactKey]
            val contact = known?.contact ?: contacts?.get(entry.contactKey) ?: return@mapNotNull null
            Suggestion(
                contact = contact,
                subtitle = known?.subtitle,
                badge = known?.badge,
                reason = entry.reason.ifBlank { known?.reason ?: "No recent calls with ${contact.name}." },
            )
        }
        val rankedIds = ranked.map { it.contact.id }.toSet()
        return ranked + fallback.filter { it.contact.id !in rankedIds }
    }
}
