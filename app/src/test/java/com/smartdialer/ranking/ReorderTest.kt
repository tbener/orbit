package com.smartdialer.ranking

import com.smartdialer.data.Contact
import com.smartdialer.data.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderTest {
    private val list = listOf("a", "b", "c", "d", "e", "f")

    private fun moved(order: List<String>, pinned: Map<String, Int>, from: Int, to: Int): List<String> =
        (Reorder.move(order, pinned, from, to) as Reorder.Result.Moved).order

    @Test
    fun movingDownShiftsTheOthersUp() {
        assertEquals(listOf("a", "c", "d", "e", "b", "f"), moved(list, emptyMap(), 2, 5))
    }

    @Test
    fun movingUpShiftsTheOthersDown() {
        assertEquals(listOf("a", "e", "b", "c", "d", "f"), moved(list, emptyMap(), 5, 2))
    }

    @Test
    fun adjacentSlotsSwap() {
        assertEquals(listOf("a", "c", "b", "d", "e", "f"), moved(list, emptyMap(), 2, 3))
        assertEquals(listOf("a", "c", "b", "d", "e", "f"), moved(list, emptyMap(), 3, 2))
    }

    @Test
    fun firstAndLastSlots() {
        assertEquals(listOf("f", "a", "b", "c", "d", "e"), moved(list, emptyMap(), 6, 1))
        assertEquals(listOf("b", "c", "d", "e", "f", "a"), moved(list, emptyMap(), 1, 6))
    }

    @Test
    fun droppingOnItsOwnSlotDoesNothing() {
        assertTrue(Reorder.move(list, emptyMap(), 3, 3) === Reorder.Result.NoOp)
    }

    @Test
    fun pinnedSlotsInBetweenStayPutAndAreSkipped() {
        val pinned = mapOf("c" to 3, "e" to 5)
        // a -> slot 6: b, d, f shift up through the unpinned slots 1, 2, 4, 6 and c, e stay.
        assertEquals(listOf("b", "d", "c", "f", "e", "a"), moved(list, pinned, 1, 6))
        // f -> slot 1: the others move down through unpinned slots only.
        assertEquals(listOf("f", "a", "c", "b", "e", "d"), moved(list, pinned, 6, 1))
    }

    @Test
    fun aPinnedTargetIsBlockedForPinnedAndUnpinnedBubbles() {
        val pinned = mapOf("c" to 3, "e" to 5)
        assertTrue(Reorder.move(list, pinned, 1, 3) === Reorder.Result.Blocked)
        assertTrue(Reorder.move(list, pinned, 3, 5) === Reorder.Result.Blocked)
    }

    @Test
    fun draggingAPinnedBubbleMovesItsPinAndKeepsTheRestInOrder() {
        val pinned = mapOf("c" to 3)
        val result = Reorder.move(list, pinned, 3, 5) as Reorder.Result.PinMoved
        assertEquals("c", result.contactKey)
        assertEquals(5, result.newSlot)
        assertEquals(listOf("a", "b", "d", "e", "c", "f"), result.order)
    }

    @Test
    fun outOfRangeSlotsDoNothing() {
        assertTrue(Reorder.move(list, emptyMap(), 0, 2) === Reorder.Result.NoOp)
        assertTrue(Reorder.move(list, emptyMap(), 2, 9) === Reorder.Result.NoOp)
    }

    @Test
    fun theManualOrderComesFirstAndIgnoresUnknownContacts() {
        fun s(id: String) = Suggestion(Contact(id, id), null, null, "")
        val ranked = list.map(::s)
        assertEquals(listOf("c", "a", "b", "d", "e", "f"), ManualOrder.apply(ranked, listOf("c", "a", "zzz")).map { it.contact.id })
        assertEquals(list, ManualOrder.apply(ranked, null).map { it.contact.id })
    }
}
