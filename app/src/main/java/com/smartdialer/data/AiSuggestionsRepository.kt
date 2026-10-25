package com.smartdialer.data

import com.smartdialer.llm.AiSettings
import com.smartdialer.llm.CachedRanking
import com.smartdialer.llm.RankingCacheStore
import com.smartdialer.llm.RankingService
import com.smartdialer.llm.RefreshOutcome
import com.smartdialer.llm.RefreshTrigger
import kotlinx.coroutines.CoroutineScope
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
) : SuggestionsRepository {

    override val suggestions: StateFlow<List<Suggestion>> = combine(
        local.suggestions,
        cache.ranking,
        settings.state,
        contacts.contacts,
    ) { fallback, ranking, state, deviceContacts ->
        SuggestionMerger.merge(
            fallback = fallback,
            ranking = ranking.takeIf { state.enabled },
            contacts = deviceContacts?.associate { it.contact.id to it.contact },
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val activity = local.activity

    /** True only while the LLM ranking call is running, not while the local call log is read. */
    override val isRefreshing: StateFlow<Boolean> = service.isRefreshing

    /**
     * Reads contacts and the call log (so the fallback list is current at once). The LLM ranking is only asked for
     * when there is no cached ranking yet; later refreshes come from the schedule, a swipe, or a changed instruction.
     */
    override suspend fun refresh() {
        local.refresh()
        service.refresh(RefreshTrigger.INITIAL)
    }

    /** The scheduled background refresh (morning and evening). */
    suspend fun refreshScheduled(): RefreshOutcome {
        local.refresh()
        return service.refresh(RefreshTrigger.SCHEDULED)
    }

    /** Swipe down on the bubbles, or "Refresh now" in Settings. Counts against the daily cap. */
    suspend fun refreshManual(): RefreshOutcome {
        local.refresh()
        return service.refresh(RefreshTrigger.MANUAL)
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
