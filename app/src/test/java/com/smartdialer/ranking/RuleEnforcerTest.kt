package com.smartdialer.ranking

import com.smartdialer.data.Contact
import com.smartdialer.data.Suggestion
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEnforcerTest {

    private val names = listOf("A", "B", "C", "D", "E", "F")
    private val contacts = (names + listOf("Brother", "Boss")).associate { it to Contact(it, it) }

    private fun suggestion(id: String) = Suggestion(contacts.getValue(id), subtitle = "s", badge = null, reason = "r")

    private val ranked = names.map(::suggestion)

    private fun ref(id: String) = ContactRef(id, id)
    private fun pin(ruleId: Long, id: String, slot: Int, createdAt: Long = ruleId) =
        TypedRule(ruleId, createdAt, TypedRuleParams.PinToSlot(ref(id), slot), null)

    private fun hide(ruleId: Long, id: String, createdAt: Long = ruleId) =
        TypedRule(ruleId, createdAt, TypedRuleParams.NeverShow(ref(id)), null)

    private fun enforce(rules: List<TypedRule>, cap: Int = 20, list: List<Suggestion> = ranked) =
        RuleEnforcer.enforce(list, rules, contacts, cap)

    private fun EnforcementResult.ids() = suggestions.map { it.contact.id }

    @Test
    fun noRulesLeavesTheListUnchangedAndAppliesTheCap() {
        assertEquals(names, enforce(emptyList()).ids())
        assertEquals(names.take(3), enforce(emptyList(), cap = 3).ids())
        assertTrue(enforce(emptyList()).trace.isEmpty())
    }

    @Test
    fun pinMovesAContactToItsSlotAndKeepsTheOthersInOrder() {
        val result = enforce(listOf(pin(1, "D", slot = 1)))
        assertEquals(listOf("D", "A", "B", "C", "E", "F"), result.ids())
        assertTrue(result.suggestions.first().isPinned)
        assertFalse(result.suggestions[1].isPinned)
        assertEquals(TraceOutcome.APPLIED, result.trace.single().outcome)
        assertEquals("D pinned to slot 1", result.trace.single().message)
    }

    @Test
    fun neverShowRemovesTheContact() {
        val result = enforce(listOf(hide(1, "B")))
        assertEquals(listOf("A", "C", "D", "E", "F"), result.ids())
        assertEquals("B hidden from suggestions", result.trace.single().message)
    }

    @Test
    fun neverShowForAContactNotInTheListChangesNothing() {
        val result = enforce(listOf(hide(1, "Boss")))
        assertEquals(names, result.ids())
    }

    @Test
    fun twoPinsOnTheSameSlot_olderRuleWins() {
        val result = enforce(listOf(pin(2, "E", slot = 1), pin(1, "C", slot = 1)))
        assertEquals("C", result.ids().first())
        assertFalse("E" == result.ids().first())
        val ignored = result.trace.single { it.outcome == TraceOutcome.IGNORED }
        assertEquals(2L, ignored.ruleId)
        assertTrue(ignored.message.contains("slot 1 is taken by C"))
    }

    @Test
    fun pinAndNeverShowOnTheSameContact_neverShowWins() {
        val result = enforce(listOf(pin(1, "B", slot = 1), hide(2, "B")))
        assertFalse("B" in result.ids())
        assertEquals(names - "B", result.ids())
        val ignoredPin = result.trace.single { it.outcome == TraceOutcome.IGNORED }
        assertEquals(1L, ignoredPin.ruleId)
        assertTrue(ignoredPin.message.contains("never-show wins"))
    }

    @Test
    fun neverShowWinsEvenWhenThePinIsNewer() {
        val result = enforce(listOf(hide(1, "B"), pin(2, "B", slot = 1)))
        assertFalse("B" in result.ids())
    }

    @Test
    fun slotLargerThanTheList_placesTheContactAtTheEnd() {
        val result = enforce(listOf(pin(1, "A", slot = 50)))
        assertEquals(listOf("B", "C", "D", "E", "F", "A"), result.ids())
        assertTrue(result.trace.single().message.contains("beyond the list"))
    }

    @Test
    fun slotLargerThanTheCap_staysWithinTheCap() {
        val result = enforce(listOf(pin(1, "A", slot = 50)), cap = 4)
        assertEquals(listOf("B", "C", "D", "A"), result.ids())
    }

    @Test
    fun severalPinsBeyondTheList_keepTheirRelativeOrder() {
        val result = enforce(listOf(pin(1, "A", slot = 9), pin(2, "B", slot = 10)))
        assertEquals(listOf("C", "D", "E", "F", "A", "B"), result.ids())
    }

    @Test
    fun pinnedContactMissingFromTheRankedList_isInsertedAtItsSlot() {
        val result = enforce(listOf(pin(1, "Brother", slot = 2)))
        assertEquals(listOf("A", "Brother", "B", "C", "D", "E", "F"), result.ids())
        assertTrue(result.trace.single().message.contains("inserted"))
        assertTrue(result.suggestions[1].isPinned)
    }

    @Test
    fun insertionRespectsTheCap() {
        val result = enforce(listOf(pin(1, "Brother", slot = 1)), cap = 3)
        assertEquals(listOf("Brother", "A", "B"), result.ids())
    }

    @Test
    fun multiplePinsShiftEachOther_everyPinLandsOnItsSlot() {
        val result = enforce(listOf(pin(1, "F", slot = 2), pin(2, "E", slot = 1), pin(3, "D", slot = 4)))
        assertEquals(listOf("E", "F", "A", "D", "B", "C"), result.ids())
    }

    @Test
    fun pinsOnAdjacentSlotsKeepTheRestInOrder() {
        val result = enforce(listOf(pin(1, "C", slot = 1), pin(2, "D", slot = 2)))
        assertEquals(listOf("C", "D", "A", "B", "E", "F"), result.ids())
    }

    @Test
    fun sameContactPinnedTwice_olderRuleWins() {
        val result = enforce(listOf(pin(1, "C", slot = 1), pin(2, "C", slot = 3)))
        assertEquals(listOf("C", "A", "B", "D", "E", "F"), result.ids())
        assertEquals(2L, result.trace.single { it.outcome == TraceOutcome.IGNORED }.ruleId)
    }

    @Test
    fun ruleForAContactThatNoLongerExists_isSkippedWithoutError() {
        val ghost = TypedRule(1, 1, TypedRuleParams.PinToSlot(ContactRef("gone", "Old Friend"), 1), null)
        val ghostHide = TypedRule(2, 2, TypedRuleParams.NeverShow(ContactRef("gone", "Old Friend")), null)
        val result = enforce(listOf(ghost, ghostHide))
        assertEquals(names, result.ids())
        assertTrue(result.trace.all { it.outcome == TraceOutcome.IGNORED && it.message.contains("contact not found") })
    }

    @Test
    fun invalidSlotIsIgnored() {
        val result = enforce(listOf(pin(1, "C", slot = 0)))
        assertEquals(names, result.ids())
        assertEquals(TraceOutcome.IGNORED, result.trace.single().outcome)
    }

    @Test
    fun resultIsIndependentOfTheOrderTheRulesAreGiven() {
        val rules = listOf(pin(1, "C", slot = 1), pin(2, "E", slot = 1), hide(3, "A"), pin(4, "F", slot = 3))
        assertEquals(enforce(rules), enforce(rules.reversed()))
    }

    @Test
    fun enforcingTwiceGivesTheSameResult() {
        val rules = listOf(pin(1, "C", slot = 2), hide(2, "A"))
        assertEquals(enforce(rules), enforce(rules))
    }

    @Test
    fun zeroCapGivesAnEmptyList() {
        assertTrue(enforce(listOf(pin(1, "A", 1)), cap = 0).suggestions.isEmpty())
    }
}
