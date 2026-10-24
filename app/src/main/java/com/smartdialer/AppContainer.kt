package com.smartdialer

import android.content.Context
import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.ContactsRepository
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.data.SuggestionsRepository
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

    val aiSettings: AiSettings = SharedPrefsAiSettings(context)
    val rankingCache: RankingCacheStore = RoomRankingCacheStore(database)
    val llmExchanges: LlmExchangeStore = RoomLlmExchangeStore(database)
    private val llmClient: LlmClient = AnthropicLlmClient(BuildConfig.LLM_API_KEY)

    private val classifier = RuleClassifier(llmClient, aiSettings, rulesRepository, contactsRepository, llmExchanges)
    val rankingService = RankingService(
        llm = llmClient,
        settings = aiSettings,
        cache = rankingCache,
        exchanges = llmExchanges,
        rules = rulesRepository,
        contacts = contactsRepository,
        local = deviceSuggestions,
        classifier = classifier,
    )

    /** The ranking source for the home screen: cached LLM ranking when on, the fallback ranker otherwise. */
    val aiSuggestions = AiSuggestionsRepository(deviceSuggestions, rankingCache, aiSettings, contactsRepository, rankingService, scope)
    val suggestionsRepository: SuggestionsRepository = aiSuggestions
    val suggestionPipeline = SuggestionPipeline(suggestionsRepository, rulesRepository, contactsRepository, scope)

    init {
        AiAutoRefresh(scope, rulesRepository, aiSettings, rankingService)
    }
}
