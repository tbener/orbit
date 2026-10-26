package com.smartdialer.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EffectValidatorTest {
    private val known = setOf("dana", "mom")
    private val byName = { name: String -> when (name.lowercase()) { "dana" -> "dana"; "mom" -> "mom"; "ghost" -> "ghost"; else -> null } }

    private fun validate(raw: RawEffect) = EffectValidator.validate(raw, byName, known)

    @Test
    fun outOfRangeValuesAreClamped() {
        val weight = validate(RawEffect("GLOBAL_WEIGHT", feature = "missedCall", multiplier = 9.0)).params as EffectParams.GlobalWeight
        assertEquals(3.0, weight.multiplier, 0.0)
        val boost = validate(RawEffect("CONTACT_BOOST", personName = "Dana", delta = -500.0)).params as EffectParams.ContactBoost
        assertEquals(-40.0, boost.delta, 0.0)
        val cadence = validate(RawEffect("CADENCE", personName = "Mom", everyDays = 9999, strength = 7.0)).params as EffectParams.Cadence
        assertEquals(365, cadence.everyDays)
        assertEquals(1.0, cadence.strength, 0.0)
        val window = validate(RawEffect("TIME_WINDOW", personName = "", weekdays = listOf("sunday", "nope"), boost = 99.0)).params as EffectParams.TimeWindow
        assertEquals(listOf("SUNDAY"), window.weekdays)
        assertNull(window.contactKey)
        assertEquals(40.0, window.boost, 0.0)
    }

    @Test
    fun unknownTypesFeaturesAndContactsAreDropped() {
        assertNull(validate(RawEffect("DELETE_EVERYTHING")).params)
        assertNull(validate(RawEffect("GLOBAL_WEIGHT", feature = "astrology", multiplier = 1.0)).params)
        assertNull(validate(RawEffect("CONTACT_BOOST", personName = "Nobody", delta = 10.0)).params)
        // A name that resolves to a key that is not a device contact is dropped too.
        assertNull(validate(RawEffect("CADENCE", personName = "Ghost", everyDays = 3)).params)
        assertNull(validate(RawEffect("TIME_WINDOW", personName = "Dana", boost = 5.0)).params) // no weekdays or parts
        assertNotNull(validate(RawEffect("CADENCE", personName = "Dana", everyDays = 21)).params)
    }

    @Test
    fun notANumberIsDropped() {
        assertNull(validate(RawEffect("GLOBAL_WEIGHT", feature = "missedCall", multiplier = Double.NaN)).params)
    }

    @Test
    fun effectsRoundTripThroughTheirStoredForm() {
        val params = listOf(
            EffectParams.GlobalWeight("missedCall", 0.0),
            EffectParams.ContactBoost("dana", 12.0, "SUNDAY", null),
            EffectParams.Cadence("mom", 21, 1.0),
            EffectParams.TimeWindow(null, listOf("SATURDAY"), listOf("EVENING"), -10.0),
        )
        params.forEach { assertEquals(it, EffectCodec.decode(EffectCodec.encode(it))) }
    }
}
