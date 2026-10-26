package com.smartdialer.ranking

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 10, 25, 10, 0, 0, 0, zone).toInstant().toEpochMilli() // a Sunday morning

    private fun busy(key: String) = ScoringInput(key, key, talks30d = 8, talks90d = 20, totalTalks = 20)
    private fun missedOnly(key: String, daysAgo: Int = 0) = ScoringInput(key, key, unreturnedMissed = 1, lastMissedDaysAgo = daysAgo)
    private fun score(inputs: List<ScoringInput>, effects: List<CompiledEffect> = emptyList(), config: ScoringConfig = ScoringConfig.DEFAULT) =
        ScoringEngine.score(inputs, effects, config, now, zone)

    private fun contribution(s: ScoredContact, feature: String) = s.contributions.firstOrNull { it.featureId == feature }?.points ?: 0.0

    @Test
    fun aMissedCallAloneCanNeverPlaceAContactFirst() {
        // Even with a huge missed-call weight and multiplier, the missed-call part is capped and the contact is not eligible for #1.
        val boosted = ScoringConfig(weights = ScoringConfig.DEFAULT_WEIGHTS + (FeatureIds.MISSED_CALL to 100.0))
        val effect = CompiledEffect(1, 1, EffectParams.GlobalWeight(FeatureIds.MISSED_CALL, 3.0))
        val ranked = score(listOf(missedOnly("missed"), ScoringInput("weak", "weak", talks30d = 3, talks90d = 6, totalTalks = 6)), listOf(effect), boosted)
        assertTrue(contribution(ranked.first { it.contactKey == "missed" }, FeatureIds.MISSED_CALL) <= ScoringDefaults.WEIGHT_MISSED_CALL)
        assertEquals("weak", ranked.first().contactKey)
    }

    @Test
    fun theMissedCallPartDecaysToZeroAfterTheSecondDay() {
        fun part(days: Int) = contribution(score(listOf(missedOnly("a", days))).single(), FeatureIds.MISSED_CALL)
        assertEquals(ScoringDefaults.WEIGHT_MISSED_CALL, part(0), 1e-9)
        assertTrue(part(1) > 0.0 && part(1) < part(0))
        assertEquals(0.0, part(2), 1e-9)
        assertEquals(0.0, part(9), 1e-9)
    }

    @Test
    fun aLongGapAloneNeverRaisesTheRank() {
        val recent = busy("recent").copy(lastContactDaysAgo = 1)
        val longGap = busy("gap").copy(lastContactDaysAgo = 120)
        val ranked = score(listOf(recent, longGap))
        assertEquals(ranked.first { it.contactKey == "recent" }.score, ranked.first { it.contactKey == "gap" }.score, 1e-9)
        assertTrue(ranked.all { r -> r.contributions.none { it.featureId == FeatureIds.CADENCE_DUE } })
    }

    @Test
    fun aCadenceMakesAContactDueOnlyWhenItExists() {
        val gap = busy("a").copy(lastContactDaysAgo = 4)
        val without = score(listOf(gap)).single()
        val cadence = CompiledEffect(1, 1, EffectParams.Cadence("a", everyDays = 3, strength = 1.0))
        val with = score(listOf(gap), listOf(cadence)).single()
        assertTrue(with.score > without.score)
        assertEquals(3, with.cadenceDays)
        // Not due yet: a recent conversation gets nothing from the cadence.
        val notDue = score(listOf(busy("a").copy(lastContactDaysAgo = 1)), listOf(cadence)).single()
        assertEquals(0.0, contribution(notDue, FeatureIds.CADENCE_DUE), 1e-9)
        // A disabled cadence is the same as none.
        assertEquals(without.score, score(listOf(gap), listOf(cadence.copy(enabled = false))).single().score, 1e-9)
    }

    @Test
    fun aGlobalWeightOfZeroRemovesThatFeature() {
        val input = busy("a").copy(unreturnedMissed = 1, lastMissedDaysAgo = 0)
        val before = score(listOf(input)).single()
        val after = score(listOf(input), listOf(CompiledEffect(1, 1, EffectParams.GlobalWeight(FeatureIds.MISSED_CALL, 0.0)))).single()
        assertTrue(contribution(before, FeatureIds.MISSED_CALL) > 0.0)
        assertEquals(0.0, contribution(after, FeatureIds.MISSED_CALL), 1e-9)
        assertFalse(after.score >= before.score)
    }

    @Test
    fun boostsAndTimeWindowsApplyOnlyWhenTheirConditionMatches() {
        val a = ScoringInput("a", "a")
        val sundayBoost = CompiledEffect(1, 1, EffectParams.ContactBoost("a", 15.0, weekday = "SUNDAY"))
        val mondayBoost = CompiledEffect(2, 1, EffectParams.ContactBoost("a", 15.0, weekday = "MONDAY"))
        val window = CompiledEffect(3, 2, EffectParams.TimeWindow("a", listOf("SUNDAY"), listOf("EVENING"), 20.0))
        assertEquals(15.0, score(listOf(a), listOf(sundayBoost, mondayBoost, window)).single().score, 1e-9)
    }

    @Test
    fun theWhatsAppFeatureIsSkippedWithoutData() {
        val s = score(listOf(ScoringInput("a", "a"))).single()
        assertTrue(s.contributions.none { it.featureId == FeatureIds.WHATSAPP })
        val withWa = score(listOf(ScoringInput("a", "a", whatsApp = WhatsAppFacts(0, null, 10)))).single()
        assertTrue(contribution(withWa, FeatureIds.WHATSAPP) > 0.0)
    }

    @Test
    fun theEngineIsDeterministic() {
        val inputs = listOf(busy("a"), busy("b"), missedOnly("c"))
        assertEquals(score(inputs), score(inputs))
    }
}
