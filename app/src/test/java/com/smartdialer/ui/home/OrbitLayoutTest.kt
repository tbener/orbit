package com.smartdialer.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitLayoutTest {

    private val widths = listOf(320f, 360f, 412f)

    @Test
    fun noSuggestionsGivesNoSlots() {
        assertTrue(OrbitLayout.compute(0, 360f).slots.isEmpty())
    }

    @Test
    fun topBubbleIsInTheMiddleOfTheRings() {
        for (w in widths) {
            val result = OrbitLayout.compute(5, w)
            val hero = result.slots.first()
            assertEquals(0, hero.index)
            assertEquals(w / 2f, hero.center.x, 0.001f)
            assertEquals(result.center, hero.center)
            assertEquals(OrbitLayout.HERO_DIAMETER, hero.diameter, 0.001f)
        }
    }

    @Test
    fun everySuggestionGetsExactlyOneSlotInOrder() {
        for (w in widths) for (count in 1..20) {
            val slots = OrbitLayout.compute(count, w).slots
            assertEquals("w=$w count=$count", (0 until count).toList(), slots.map { it.index })
        }
    }

    @Test
    fun everythingStaysOnScreenHorizontallyAndBelowTheTop() {
        for (w in widths) {
            for (slot in OrbitLayout.compute(20, w).slots) {
                val box = slot.footprint
                assertTrue("left ${slot.index} w=$w", box.left >= 0f)
                assertTrue("right ${slot.index} w=$w", box.right <= w)
                assertTrue("top ${slot.index} w=$w", box.top >= 0f)
            }
        }
    }

    @Test
    fun noTwoBubblesOrNamesOverlap() {
        for (w in widths) for (count in listOf(7, 14, 20)) {
            val slots = OrbitLayout.compute(count, w).slots
            for (i in slots.indices) for (j in i + 1 until slots.size) {
                assertTrue(
                    "overlap ${slots[i].index} vs ${slots[j].index} (w=$w, count=$count)",
                    !slots[i].footprint.overlaps(slots[j].footprint),
                )
            }
        }
    }

    @Test
    fun theFirstTwoAfterTheTopBubbleSitOnItsLeftAndRight() {
        val slots = OrbitLayout.compute(4, 360f).slots
        assertTrue(slots[1].center.x < slots[0].center.x)
        assertTrue(slots[2].center.x > slots[0].center.x)
        assertEquals(slots[0].center.y, slots[1].center.y, 0.5f)
    }

    @Test
    fun ringsGetSmallerAndLiveFurtherOut() {
        val slots = OrbitLayout.compute(20, 360f).slots
        val byRing = slots.drop(1).groupBy { it.ring }
        val diameters = byRing.toSortedMap().values.map { it.first().diameter }
        assertEquals(diameters.sortedDescending(), diameters)
        assertTrue(byRing.keys.size >= 2)
    }

    @Test
    fun theLayoutIsDeterministic() {
        assertEquals(OrbitLayout.compute(20, 360f), OrbitLayout.compute(20, 360f))
    }

    @Test
    fun contentIsTallEnoughForEveryBubble() {
        val result = OrbitLayout.compute(20, 360f)
        assertTrue(result.contentHeight >= result.slots.maxOf { it.footprint.bottom })
    }

    @Test
    fun ringsDrawnMatchTheRingsUsed() {
        val result = OrbitLayout.compute(7, 360f)
        assertTrue(result.ringRadii.size >= result.slots.maxOf { it.ring })
        assertEquals(result.ringRadii.sorted(), result.ringRadii)
    }

    @Test
    fun largeFontsKeepNamesFromOverlapping() {
        for (w in widths) {
            val slots = OrbitLayout.compute(20, w, labelScale = 1.5f).slots
            for (i in slots.indices) for (j in i + 1 until slots.size) {
                assertTrue("overlap ${slots[i].index} vs ${slots[j].index} (w=$w)", !slots[i].footprint.overlaps(slots[j].footprint))
            }
        }
    }
}
