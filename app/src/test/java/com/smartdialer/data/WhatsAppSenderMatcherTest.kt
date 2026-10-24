package com.smartdialer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppSenderMatcherTest {
    private fun c(key: String, name: String, vararg numbers: String) = DeviceContact(Contact(key, name), numbers.toList())

    private val contacts = listOf(
        c("mom", "Mom", "+972 50-111-1111"),
        c("ron1", "Ron Cohen", "050-222-2222"),
        c("ron2", "Ron Levi", "050-333-3333"),
        c("dana", "Dana Katz", "052-444-4444"),
        c("dana2", "Dana", "052-555-5555"),
    )

    private fun key(sender: String) = (WhatsAppSenderMatcher.match(sender, contacts) as? SenderMatch.Found)?.contactKey

    @Test
    fun exactNormalizedNameMatches() {
        assertEquals("mom", key("  MOM "))
        assertEquals("ron1", key("ron  cohen"))
        assertEquals("mom", key("~Mom"))
    }

    @Test
    fun exactBeatsPrefix() {
        assertEquals("dana2", key("Dana"))
    }

    @Test
    fun uniquePrefixMatchesAndAmbiguousOnesDoNot() {
        assertEquals("dana", key("Dana Katz"))
        assertEquals(SenderMatch.Ambiguous, WhatsAppSenderMatcher.match("Ron", contacts))
    }

    @Test
    fun phoneNumbersMatchByDigits() {
        assertEquals("mom", key("+972 50-111-1111"))
        assertEquals("ron1", key("0502222222"))
        assertEquals(SenderMatch.None, WhatsAppSenderMatcher.match("+1 555 000 9999", contacts))
    }

    @Test
    fun unmatchedAndGroupStyleSendersAreDropped() {
        assertEquals(SenderMatch.None, WhatsAppSenderMatcher.match("Stranger", contacts))
        assertEquals(SenderMatch.None, WhatsAppSenderMatcher.match("Family: Mom", contacts))
        assertEquals(SenderMatch.None, WhatsAppSenderMatcher.match("   ", contacts))
    }

    @Test
    fun groupSummaryAndCallNotificationsAreRejected() {
        fun facts(category: String? = "msg", summary: Boolean = false, group: Boolean = false, title: Boolean = false) =
            NotificationFacts(category, summary, group, title)
        assertTrue(WhatsAppNotificationRules.accept(facts()))
        assertTrue(WhatsAppNotificationRules.accept(facts(category = null)))
        assertFalse(WhatsAppNotificationRules.accept(facts(group = true)))
        assertFalse(WhatsAppNotificationRules.accept(facts(title = true)))
        assertFalse(WhatsAppNotificationRules.accept(facts(summary = true)))
        assertFalse(WhatsAppNotificationRules.accept(facts(category = "call")))
    }
}
