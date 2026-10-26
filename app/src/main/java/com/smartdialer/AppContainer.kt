package com.smartdialer

import android.content.Context
import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.data.SuggestionsRepository
import com.smartdialer.data.WhatsAppEvent
import com.smartdialer.data.WhatsAppEventStore
import com.smartdialer.data.WhatsAppSettings
import com.smartdialer.data.db.RoomWhatsAppEventStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.smartdialer.data.db.OrbitDatabase
import com.smartdialer.data.db.RoomLlmExchangeStore
import com.smartdialer.data.db.RoomRankingCacheStore
import com.smartdialer.data.db.RoomRulesRepository
import com.smartdialer.data.device.DeviceCallLogSource
import com.smartdialer.data.device.DeviceContactsRepository
import com.smartdialer.data.device.DeviceContactsSource
import com.smartdialer.data.device.DeviceSuggestionsRepository
import com.smartdialer.llm.AiAutoRefresh
import com.smartdialer.llm.AiSettings
import com.smartdialer.llm.AnthropicLlmClient
import com.smartdialer.llm.LlmClient
import com.smartdialer.llm.LlmExchangeStore
import com.smartdialer.llm.RankingCacheStore
import com.smartdialer.llm.RankingService
import com.smartdialer.llm.RuleClassifier
import com.smartdialer.llm.SharedPrefsAiSettings
import com.smartdialer.rules.RulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons. Created once by [OrbitApplication]. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolver = context.contentResolver
    private val database = OrbitDatabase.create(context)

    val rulesRepository: RulesRepository = RoomRulesRepository(database)
    val contactsRepository: ContactsRepository = DeviceContactsRepository(DeviceContactsSource(resolver))

    private val deviceSuggestions: SuggestionsRepository =
        DeviceSuggestionsRepository(contactsRepository, DeviceCallLogSource(resolver))

    val whatsAppStore: WhatsAppEventStore = RoomWhatsAppEventStore(database)
    val whatsAppSettings = WhatsAppSettings(context)

    /** WhatsApp events, or an empty list while the feature is switched off. */
    val whatsAppEvents: Flow<List<WhatsAppEvent>> =
        combine(whatsAppSettings.state, whatsAppStore.events) { settings, events -> if (settings.enabled) events else emptyList() }

    val uiSettings = com.smartdialer.data.UiSettings(context)
    val manualArrangement: com.smartdialer.data.ManualArrangement = com.smartdialer.data.PrefsManualArrangement(context)
    val aiSettings: AiSettings = SharedPrefsAiSettings(context)
    val rankingCache: RankingCacheStore = RoomRankingCacheStore(database)
    val llmExchanges: LlmExchangeStore = RoomLlmExchangeStore(database)
    private val llmClient: LlmClient = AnthropicLlmClient(BuildConfig.LLM_API_KEY)

    val classifier = RuleClassifier(llmClient, aiSettings, rulesRepository, contactsRepository, llmExchanges)
    val rankingService = RankingService(
        llm = llmClient,
        settings = aiSettings,
        cache = rankingCache,
        exchanges = llmExchanges,
        rules = rulesRepository,
        contacts = contactsRepository,
        local = deviceSuggestions,
        classifier = classifier,
        whatsApp = whatsAppStore,
        whatsAppEnabled = { whatsAppSettings.state.value.enabled },
        onRankingUpdated = { manualArrangement.clear() },
    )

    val engineSettings = com.smartdialer.data.EngineSettings(context)
    val localRanking = com.smartdialer.data.LocalRanking(deviceSuggestions, contactsRepository, rulesRepository, engineSettings, whatsAppEvents, scope)

    /** The ranking source for the home screen: the local engine by default, or the cached LLM ranking in AI mode. */
    val aiSuggestions = AiSuggestionsRepository(
        deviceSuggestions, rankingCache, aiSettings, contactsRepository, rankingService, scope, localRanking, engineSettings,
    )
    val suggestionsRepository: SuggestionsRepository = aiSuggestions
    val suggestionPipeline = SuggestionPipeline(suggestionsRepository, rulesRepository, contactsRepository, scope, manualArrangement)

    init {
        AiAutoRefresh(scope, rulesRepository, aiSettings, rankingService, classifier, engineSettings, localRanking)
        scope.launch { whatsAppStore.pruneOld(System.currentTimeMillis()) }
    }
}
