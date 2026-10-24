package com.smartdialer.llm

import com.smartdialer.data.Contact
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingLogicTest {
    private val keys = setOf("a", "b", "c")

    private fun valid(text: String) = RankingParser.parse(text, keys) as RankingParse.Valid

    @Test
    fun validAnswerIsParsed() {
        val r = valid(rankingJson(Triple("a", 80, "x"), Triple("b", 20, "y")))
        assertEquals(listOf("a", "b"), r.entries.map { it.contactKey })
        assertTrue(r.issues.any { "not ranked" in it })
    }

    @Test
    fun malformedAnswersAreUnusable() {
        assertTrue(RankingParser.parse("nope", keys) is RankingParse.Unusable)
        assertTrue(RankingParser.parse("{\"x\":1}", keys) is RankingParse.Unusable)
        assertTrue(RankingParser.parse(rankingJson(Triple("zzz", 1, "")), keys) is RankingParse.Unusable)
    }

    @Test
    fun unknownAndDuplicateKeysAreDropped() {
        val r = valid(rankingJson(Triple("a", 50, "x"), Triple("a", 90, "dup"), Triple("zzz", 10, "u")))
        assertEquals(1, r.entries.size)
        assertEquals(50, r.entries.single().score)
        assertEquals(2, r.issues.count { "dropped" in it })
    }

    @Test
    fun valuesAreClamped() {
        val r = valid("""{"ranking":[{"contactKey":"a","score":250,"tier":"TOP","reason":"  hi  "},{"contactKey":"b","score":-5,"tier":"weird","reason":""}]}""")
        assertEquals(100, r.entries[0].score)
        assertEquals(Tier.TOP, r.entries[0].tier)
        assertEquals("hi", r.entries[0].reason)
        assertEquals(0, r.entries[1].score)
        assertEquals(Tier.LOW, r.entries[1].tier)
    }

    @Test
    fun omittedContactsAreFilledFromFallbackOrder() {
        val parsed = valid(rankingJson(Triple("c", 40, "c"))).entries
        val merged = RankingMerge.merge(parsed, listOf("a", "b", "c"), listOf("b", "a"), mapOf("b" to "fb"), 5L)
        assertEquals(listOf("c", "b", "a"), merged.map { it.contactKey })
        assertEquals(listOf(1, 2, 3), merged.map { it.rank })
        assertEquals(EntrySource.FALLBACK, merged[1].source)
        assertEquals("fb", merged[1].reason)
    }

    private val contacts = listOf("a", "b", "c", "hid").associateWith { Contact(it, it.uppercase()) }
    private val zone = ZoneId.of("UTC")

    private fun select(typed: List<TypedRule> = emptyList(), free: List<FreeTextRule> = emptyList(), max: Int = 100) =
        CandidateSelector.select(contacts, mapOf("a" to activity(), "b" to activity(), "hid" to activity()), free, typed, 2_000_000L, zone, max)

    @Test
    fun neverShowContactsAreExcludedEvenWithInstructions() {
        val hide = TypedRule(1, 1, TypedRuleParams.NeverShow(ContactRef("hid", "HID")), null)
        val note = FreeTextRule(2, "call often", 2, FreeTextStatus.SOFT, null, "hid")
        assertFalse(select(listOf(hide), listOf(note)).candidates.any { it.contact.id == "hid" })
    }

    @Test
    fun instructionContactsWithoutHistoryAreIncludedAndCapApplies() {
        val note = FreeTextRule(2, "call c", 2, FreeTextStatus.SOFT, null, "c")
        assertTrue(select(free = listOf(note)).candidates.any { it.contact.id == "c" })
        assertEquals(2, select(max = 2).candidates.size)
    }

    @Test
    fun payloadHasNoNumbersOrPhotos() {
        val text = RankingPrompt.userMessage(select())
        assertFalse(text.contains("+1000"))
        assertFalse(text.contains("photo", ignoreCase = true))
        assertTrue(text.contains("contactKey"))
    }

    @Test
    fun hashIgnoresClockButNotData() {
        val a = select()
        val later = CandidateSelector.select(contacts, mapOf("a" to activity(), "b" to activity(), "hid" to activity()), emptyList(), emptyList(), 9_000_000L, zone)
        assertEquals(RankingPrompt.inputsHash(a), RankingPrompt.inputsHash(later))
        val changed = CandidateSelector.select(contacts, mapOf("a" to activity(missed = 1), "b" to activity(), "hid" to activity()), emptyList(), emptyList(), 2L, zone)
        assertFalse(RankingPrompt.inputsHash(a) == RankingPrompt.inputsHash(changed))
    }

    private val cached = CachedRanking(emptyList(), "h", Prompts.RANKING_VERSION, LlmConfig.MODEL, 1_000L)

    private fun decide(now: Long, hash: String = "h", fail: Long? = null, force: Boolean = false, c: CachedRanking? = cached) =
        RankingFreshness.decide(c, hash, Prompts.RANKING_VERSION, LlmConfig.MODEL, now, fail, force)

    @Test
    fun freshness() {
        assertEquals(RankingFreshness.Decision.FRESH, decide(2_000L))
        assertEquals(RankingFreshness.Decision.REFRESH, decide(1_000L + LlmConfig.STALE_AFTER_MILLIS + 1))
        assertEquals(RankingFreshness.Decision.REFRESH, decide(2_000L, hash = "other"))
        assertEquals(RankingFreshness.Decision.REFRESH, decide(2_000L, c = null))
        assertEquals(RankingFreshness.Decision.BACKING_OFF, decide(2_000L, hash = "other", fail = 1_500L))
        assertEquals(RankingFreshness.Decision.REFRESH, decide(2_000L, fail = 1_500L, force = true))
    }
}
