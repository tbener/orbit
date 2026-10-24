package com.smartdialer.llm

import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.Contact
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.TypedRuleParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RankingServiceTest {
    private val people = listOf("a", "b", "c").map { Contact(it, it.uppercase()) }
    private val fallback = people.map { Suggestion(it, "Today", null, "fallback ${it.id}") }

    private class Env(val client: FakeLlmClient, people: List<Contact>, fallback: List<Suggestion>, val settings: InMemoryAiSettings, var now: Long = 10_000_000L) {
        val cache = InMemoryRankingCacheStore()
        val rules = FakeRules()
        val contacts = FakeContacts(people)
        val local = FakeLocal(fallback, people.associate { it.id to activity() })
        val service = RankingService(client, settings, cache, InMemoryLlmExchangeStore(), rules, contacts, local, null, { now })
    }

    private fun env(client: FakeLlmClient, settings: InMemoryAiSettings = InMemoryAiSettings()) = Env(client, people, fallback, settings)

    private val good = rankingJson(Triple("c", 90, "C first"), Triple("a", 50, "A next"), Triple("b", 10, "B last"))

    @Test
    fun refreshStoresTheRankingThenIsFreshUntilInputsChangeOrItGoesStale() = runTest {
        val e = env(FakeLlmClient.answering(good))
        assertTrue(e.service.refresh() is RefreshOutcome.Updated)
        assertEquals(listOf("c", "a", "b"), e.cache.load()!!.entries.map { it.contactKey })
        assertEquals(Prompts.RANKING_VERSION, e.cache.load()!!.promptVersion)

        assertEquals(SkipReason.FRESH, (e.service.refresh() as RefreshOutcome.Skipped).reason)
        e.local.activityState.value = people.associate { it.id to activity(missed = 1) }
        assertTrue(e.service.refresh() is RefreshOutcome.Updated)
        e.now += LlmConfig.STALE_AFTER_MILLIS + 1
        assertTrue(e.service.refresh() is RefreshOutcome.Updated)
        assertEquals(3, e.client.requests.size)
    }

    @Test
    fun failureKeepsTheLastGoodRankingRecordsItAndBacksOff() = runTest {
        var fail = false
        val client = FakeLlmClient { req ->
            if (fail) LlmResult.Failure(FailureKind.NETWORK, "down", AnthropicRequest.body(req))
            else LlmResult.Success(good, AnthropicRequest.body(req), "{}")
        }
        val e = env(client)
        e.service.refresh()
        val before = e.cache.load()
        fail = true
        assertTrue(e.service.refresh(force = true) is RefreshOutcome.Failed)
        assertEquals(before, e.cache.load())
        assertNotNull(e.settings.state.value.lastFailureAt)
        e.local.activityState.value = people.associate { it.id to activity(missed = 2) }
        assertEquals(SkipReason.BACKING_OFF, (e.service.refresh() as RefreshOutcome.Skipped).reason)
    }

    @Test
    fun unusableAnswerKeepsTheCache() = runTest {
        val e = env(FakeLlmClient.answering("garbage"))
        assertTrue(e.service.refresh() is RefreshOutcome.Failed)
        assertEquals(null, e.cache.load())
    }

    @Test
    fun noNetworkCallsWhenOffOrNotAcknowledged() = runTest {
        val off = env(FakeLlmClient.answering(good), InMemoryAiSettings(AiSettingsState(enabled = false, disclosureAcknowledged = true)))
        assertEquals(SkipReason.AI_OFF, (off.service.refresh(force = true) as RefreshOutcome.Skipped).reason)
        val fresh = env(FakeLlmClient.answering(good), InMemoryAiSettings(AiSettingsState()))
        assertEquals(SkipReason.NOT_ACKNOWLEDGED, (fresh.service.refresh(force = true) as RefreshOutcome.Skipped).reason)
        assertTrue(off.client.requests.isEmpty() && fresh.client.requests.isEmpty())
    }

    @Test
    fun homeFlowShowsFallbackThenCachedThenUpdatedAndAlwaysEnforcesPins() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val e = env(FakeLlmClient.answering(good))
        e.rules.addTypedRule(TypedRuleParams.PinToSlot(ContactRef("b", "B"), 1))
        val repo = AiSuggestionsRepository(e.local, e.cache, e.settings, e.contacts, e.service, scope.backgroundScope)
        val pipeline = SuggestionPipeline(repo, e.rules, e.contacts, scope.backgroundScope)

        // Before any AI result: the fallback order, with the pin already applied.
        assertEquals(listOf("b", "a", "c"), pipeline.result.value.suggestions.map { it.contact.id })

        repo.refresh()
        // The model ranked B last, but the pin still wins; the rest follow the model.
        val after = pipeline.result.value.suggestions
        assertEquals(listOf("b", "c", "a"), after.map { it.contact.id })
        assertEquals("C first", after[1].reason)
        assertEquals("Today", after[1].subtitle)
        assertTrue(after[0].isPinned)

        // A new hard rule applies immediately, without another LLM call.
        e.rules.addTypedRule(TypedRuleParams.NeverShow(ContactRef("c", "C")))
        assertEquals(listOf("b", "a"), pipeline.result.value.suggestions.map { it.contact.id })
        assertEquals(1, e.client.requests.size)
        assertEquals(false, repo.isRefreshing.first())
    }
}
