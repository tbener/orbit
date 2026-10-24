package com.smartdialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppLinkTest {

    /** Stands in for the platform formatter: treats a leading 0 as the trunk prefix of country code 972. */
    private val israel: (String) -> String? = { local ->
        val digits = local.filter { it.isDigit() }
        if (digits.startsWith("0")) "+972" + digits.drop(1) else null
    }

    private val neverConverts: (String) -> String? = { null }

    @Test
    fun plusNumberKeepsOnlyDigits() {
        assertEquals("972501111111", WhatsAppLink.internationalDigits("+972 50-111-1111", neverConverts))
    }

    @Test
    fun doubleZeroPrefixIsDropped() {
        assertEquals("972501111111", WhatsAppLink.internationalDigits("00972501111111", neverConverts))
    }

    @Test
    fun localNumberIsConvertedByTheFormatter() {
        assertEquals("972501111111", WhatsAppLink.internationalDigits("050-111-1111", israel))
        assertEquals("972501111111", WhatsAppLink.internationalDigits("(050) 111 1111", israel))
    }

    @Test
    fun localNumberTheFormatterCannotConvertGivesNull() {
        assertNull(WhatsAppLink.internationalDigits("050-111-1111", neverConverts))
    }

    @Test
    fun numbersWithoutDigitsOrWithImplausibleLengthGiveNull() {
        assertNull(WhatsAppLink.internationalDigits("", israel))
        assertNull(WhatsAppLink.internationalDigits("abc", israel))
        assertNull(WhatsAppLink.internationalDigits("+12", neverConverts))
        assertNull(WhatsAppLink.internationalDigits("+1234567890123456", neverConverts))
    }

    @Test
    fun internationalNumberDoesNotCallTheFormatter() {
        var called = false
        WhatsAppLink.internationalDigits("+972501111111") { called = true; null }
        assertEquals(false, called)
    }

    @Test
    fun urlUsesWaMe() {
        assertEquals("https://wa.me/972501111111", WhatsAppLink.url("972501111111"))
    }

    @Test
    fun prefersWhatsAppOverBusiness() {
        assertEquals("com.whatsapp", WhatsAppLink.choosePackage(setOf("com.whatsapp.w4b", "com.whatsapp")))
        assertEquals("com.whatsapp.w4b", WhatsAppLink.choosePackage(setOf("com.whatsapp.w4b")))
        assertNull(WhatsAppLink.choosePackage(emptySet()))
    }
}
