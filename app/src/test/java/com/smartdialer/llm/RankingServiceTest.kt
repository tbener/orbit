package com.smartdialer.llm

import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.Contact
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRuleParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RankingServiceTest {
    private val people = listOf("a", "b", "c").map { Contact(it, it.uppercase()) }
    private val fallback = people.map { Suggestion(it, "Today", null, "fallback ${it.id}") }

    private class Env(val client: FakeLlmClient, people: List<Contact>, fallback: List<Suggestion>, val settings: InMemoryAiSettings, var now: Long = 10_000_000L) {
        val cache = InMemoryRankingCacheStore()
        val rules = FakeRules()
        val contacts = FakeContacts(people)
        val local = FakeLocal(fallback, people.associate { it.id to activity() })
        val service = RankingService(client, settings, cache, InMemoryLlmExchangeStore(), rules, contacts, local, null, { now }, { ZoneId.of("UTC") })
    }

    private fun env(client: FakeLlmClient, settings: InMemoryAiSettings = InMemoryAiSettings()) = Env(client, people, fallback, settings)

    private val good = rankingJson(Triple("c", 90, "C first"), Triple("a", 50, "A next"), Triple("b", 10, "B last"))

    @Test
    fun aRefreshStoresTheRankingAndCountsAgainstTheDailyCap() = runTest {
        val e = env(FakeLlmClient.answering(good))
        assertTrue(e.service.refresh(RefreshTrigger.SCHEDULED) is RefreshOutcome.Updated)
        assertEquals(listOf("c", "a", "b"), e.cache.load()!!.entries.map { it.contactKey })
        assertEquals(Prompts.RANKING_VERSION, e.cache.load()!!.promptVersion)
        assertEquals(1, e.settings.state.value.refreshCount)
        assertTrue(e.service.refresh(RefreshTrigger.MANUAL) is RefreshOutcome.Updated)
        assertEquals(2, e.settings.state.value.refreshCount)
    }

    @Test
    fun theDailyCapSkipsEveryTriggerAndResetsAtLocalMidnight() = runTest {
        val today = LocalDate.of(1970, 1, 1).plusDays(10_000_000L / 86_400_000L)
        val full = AiSettingsState(disclosureAcknowledged = true, refreshDay = RefreshQuota.dayKey(today), refreshCount = RefreshQuota.DAILY_CAP)
        val e = env(FakeLlmClient.answering(good), InMemoryAiSettings(full))
        for (trigger in RefreshTrigger.entries) {
            assertEquals(SkipReason.DAILY_CAP, (e.service.refresh(trigger) as RefreshOutcome.Skipped).reason)
        }
        assertTrue(e.client.requests.isEmpty())
        e.now += 86_400_000L
        assertTrue(e.service.refresh(RefreshTrigger.MANUAL) is RefreshOutcome.Updated)
        assertEquals(1, e.settings.state.value.refreshCount)
    }

    @Test
    fun quotaMath() {
        val s = AiSettingsState(refreshDay = "2026-10-25", refreshCount = 19)
        assertTrue(RefreshQuota.canStart(s, LocalDate.of(2026, 10, 25)))
        assertEquals(false, RefreshQuota.canStart(s.copy(refreshCount = 20), LocalDate.of(2026, 10, 25)))
        assertEquals(0, RefreshQuota.countToday(s, LocalDate.of(2026, 10, 26)))
        assertTrue(RefreshQuota.canStart(s.copy(refreshCount = 20), LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun gateQueuesExactlyOneFollowUpAndIgnoresManualPulls() {
        val gate = RefreshGate()
        assertEquals(RefreshGate.Start.STARTED, gate.tryStart(RefreshTrigger.SCHEDULED))
        assertEquals(RefreshGate.Start.QUEUED, gate.tryStart(RefreshTrigger.INSTRUCTION))
        assertEquals(RefreshGate.Start.QUEUED, gate.tryStart(RefreshTrigger.INSTRUCTION))
        assertEquals(RefreshGate.Start.IGNORED, gate.tryStart(RefreshTrigger.MANUAL))
        assertTrue(gate.nextOrFinish()) // the one follow-up
        assertEquals(false, gate.nextOrFinish()) // then idle
        assertEquals(RefreshGate.Start.STARTED, gate.tryStart(RefreshTrigger.MANUAL))
    }

    @Test
    fun aTriggerDuringARunQueuesOneFollowUpWithFreshInputsAndNeverRunsInParallel() = runTest {
        val e = env(FakeLlmClient.answering(good, latencyMillis = 100))
        val first = async { e.service.refresh(RefreshTrigger.SCHEDULED) }
        advanceTimeBy(10)
        val id = e.rules.addFreeTextRule("call Mom more")
        e.rules.setClassification(id, FreeTextStatus.SOFT, "soft")
        assertEquals(SkipReason.QUEUED, (e.service.refresh(RefreshTrigger.INSTRUCTION) as RefreshOutcome.Skipped).reason)
        assertEquals(SkipReason.QUEUED, (e.service.refresh(RefreshTrigger.INSTRUCTION) as RefreshOutcome.Skipped).reason)
        assertEquals(SkipReason.BUSY, (e.service.refresh(RefreshTrigger.MANUAL) as RefreshOutcome.Skipped).reason)
        first.await()
        assertEquals(2, e.client.requests.size) // the first run and exactly one follow-up
        assertTrue(e.client.requests.last().user.contains("call Mom more"))
    }

    @Test
    fun instructionRefreshesOnlyWhenTheLlmInputChanged() = runTest {
        val e = env(FakeLlmClient.answering(good))
        assertTrue(e.service.refresh(RefreshTrigger.INSTRUCTION) is RefreshOutcome.Updated)
        // A hard rule changes nothing the model sees.
        e.rules.addTypedRule(TypedRuleParams.PinToSlot(ContactRef("b", "B"), 1))
        assertEquals(SkipReason.UNCHANGED, (e.service.refresh(RefreshTrigger.INSTRUCTION) as RefreshOutcome.Skipped).reason)
        val id = e.rules.addFreeTextRule("only evenings")
        e.rules.setClassification(id, FreeTextStatus.SOFT, "soft")
        assertTrue(e.service.refresh(RefreshTrigger.INSTRUCTION) is RefreshOutcome.Updated)
        assertEquals(2, e.client.requests.size)
    }

    @Test
    fun theInitialRefreshOnlyRunsWithoutACache() = runTest {
        val e = env(FakeLlmClient.answering(good))
        assertTrue(e.service.refresh(RefreshTrigger.INITIAL) is RefreshOutcome.Updated)
        assertEquals(SkipReason.HAS_CACHE, (e.service.refresh(RefreshTrigger.INITIAL) as RefreshOutcome.Skipped).reason)
    }

    @Test
    fun failureKeepsTheLastGoodRankingAndRecordsIt() = runTest {
        var fail = false
        val client = FakeLlmClient { req ->
            if (fail) LlmResult.Failure(FailureKind.NETWORK, "down", AnthropicRequest.body(req))
            else LlmResult.Success(good, AnthropicRequest.body(req), "{}")
        }
        val e = env(client)
        e.service.refresh(RefreshTrigger.SCHEDULED)
        val before = e.cache.load()
        fail = true
        assertTrue(e.service.refresh(RefreshTrigger.MANUAL) is RefreshOutcome.Failed)
        assertEquals(before, e.cache.load())
        assertNotNull(e.settings.state.value.lastFailureAt)
    }

    @Test
    fun noNetworkCallsWhenOffOrNotAcknowledged() = runTest {
        val off = env(FakeLlmClient.answering(good), InMemoryAiSettings(AiSettingsState(enabled = false, disclosureAcknowledged = true)))
        assertEquals(SkipReason.AI_OFF, (off.service.refresh(RefreshTrigger.MANUAL) as RefreshOutcome.Skipped).reason)
        val fresh = env(FakeLlmClient.answering(good), InMemoryAiSettings(AiSettingsState()))
        assertEquals(SkipReason.NOT_ACKNOWLEDGED, (fresh.service.refresh(RefreshTrigger.MANUAL) as RefreshOutcome.Skipped).reason)
        assertTrue(off.client.requests.isEmpty() && fresh.client.requests.isEmpty())
    }

    @Test
    fun homeFlowShowsFallbackThenCachedThenUpdatedAndAlwaysEnforcesPins() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val e = env(FakeLlmClient.answering(good))
        e.rules.addTypedRule(TypedRuleParams.PinToSlot(ContactRef("b", "B"), 1))
        val engine = com.smartdialer.data.EngineSettings(null).also { it.setMode(com.smartdialer.data.EngineMode.AI) }
        val localRanking = com.smartdialer.data.LocalRanking(e.local, e.contacts, e.rules, engine, kotlinx.coroutines.flow.flowOf(emptyList()), scope.backgroundScope)
        val repo = AiSuggestionsRepository(e.local, e.cache, e.settings, e.contacts, e.service, scope.backgroundScope, localRanking, engine)
        val pipeline = SuggestionPipeline(repo, e.rules, e.contacts, scope.backgroundScope)

        // Before any AI result: the fallback order, with the pin already applied.
        assertEquals(listOf("b", "a", "c"), pipeline.result.value.suggestions.map { it.contact.id })

        repo.refresh() // opening the app: no cache yet, so one initial refresh
        // The model ranked B last, but the pin still wins; the rest follow the model.
        val after = pipeline.result.value.suggestions
        assertEquals(listOf("b", "c", "a"), after.map { it.contact.id })
        assertEquals("C first", after[1].reason)
        assertEquals("Today", after[1].subtitle)
        assertTrue(after[0].isPinned)

        // A new hard rule applies immediately, without another LLM call; opening again does not ask the model.
        e.rules.addTypedRule(TypedRuleParams.NeverShow(ContactRef("c", "C")))
        assertEquals(listOf("b", "a"), pipeline.result.value.suggestions.map { it.contact.id })
        repo.refresh()
        assertEquals(1, e.client.requests.size)
        assertEquals(false, repo.isRefreshing.first())
    }
}
