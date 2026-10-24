package com.smartdialer.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedRuleCodecTest {

    private val ref = ContactRef("lookup", "Mom")

    @Test
    fun pinRoundTrips() {
        val params = TypedRuleParams.PinToSlot(ref, 3)
        assertEquals(params, TypedRuleCodec.decode(TypedRuleCodec.encode(params)))
    }

    @Test
    fun neverShowRoundTrips() {
        val params = TypedRuleParams.NeverShow(ref)
        assertEquals(params, TypedRuleCodec.decode(TypedRuleCodec.encode(params)))
    }

    @Test
    fun jsonIsTaggedWithTheTypeName() {
        assertTrue(TypedRuleCodec.encode(TypedRuleParams.PinToSlot(ref, 1)).contains("\"kind\":\"PIN_TO_SLOT\""))
        assertTrue(TypedRuleCodec.encode(TypedRuleParams.NeverShow(ref)).contains("\"kind\":\"NEVER_SHOW\""))
    }

    @Test
    fun typeIsDerivedFromTheParams() {
        assertEquals(TypedRuleType.PIN_TO_SLOT, TypedRuleParams.PinToSlot(ref, 1).type)
        assertEquals(TypedRuleType.NEVER_SHOW, TypedRuleParams.NeverShow(ref).type)
    }
}
