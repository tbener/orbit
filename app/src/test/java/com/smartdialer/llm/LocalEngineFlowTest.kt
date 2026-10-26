package com.smartdialer.llm

import com.smartdialer.data.AiSuggestionsRepository
import com.smartdialer.data.Contact
import com.smartdialer.data.EngineMode
import com.smartdialer.data.EngineSettings
import com.smartdialer.data.LocalRanking
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionPipeline
import com.smartdialer.data.ActivityStats
import com.smartdialer.data.ContactActivity
import com.smartdialer.data.ContactSignals
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.TypedRuleParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalEngineFlowTest {
    private val people = listOf("a", "b", "c", "d").map { Contact(it, it.uppercase()) }

    private fun activity(talks30: Int) = ContactActivity(
        ContactSignals(1_000_000L, 0, null, talks30, talkCount = talks30),
        emptyMap(),
        ActivityStats(talks30d = talks30, talks90d = talks30 * 2, habits = com.smartdialer.ranking.HabitFacts.NONE),
    )

    @Test
    fun theLocalOrderStillPassesThroughPinsAndHides() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val fallback = people.map { Suggestion(it, null, null, "fb") }
        val local = FakeLocal(fallback, mapOf("a" to activity(8), "b" to activity(6), "c" to activity(4), "d" to activity(2)))
        val rules = FakeRules()
        val contacts = FakeContacts(people)
        val engine = EngineSettings(null).also { it.setMode(EngineMode.LOCAL) }
        val localRanking = LocalRanking(local, contacts, rules, engine, flowOf(emptyList()), scope.backgroundScope, { 10_000_000L })
        val service = RankingService(FakeLlmClient.failing(), InMemoryAiSettings(), InMemoryRankingCacheStore(), InMemoryLlmExchangeStore(), rules, contacts, local, null, { 10_000_000L })
        val repo = AiSuggestionsRepository(local, InMemoryRankingCacheStore(), InMemoryAiSettings(), contacts, service, scope.backgroundScope, localRanking, engine)
        val pipeline = SuggestionPipeline(repo, rules, contacts, scope.backgroundScope)

        // The engine puts the most frequent first.
        assertEquals(listOf("a", "b", "c", "d"), pipeline.result.value.suggestions.map { it.contact.id })
        // A pin and a hide are applied on top of it, unchanged.
        rules.addTypedRule(TypedRuleParams.PinToSlot(ContactRef("d", "D"), 1))
        rules.addTypedRule(TypedRuleParams.NeverShow(ContactRef("b", "B")))
        assertEquals(listOf("d", "a", "c"), pipeline.result.value.suggestions.map { it.contact.id })
    }
}
