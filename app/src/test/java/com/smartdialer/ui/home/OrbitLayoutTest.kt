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
    fun topBubbleIsLargestAndCentered() {
        for (w in widths) {
            val slots = OrbitLayout.compute(10, w).slots
            val hero = slots.first()
            assertEquals(0, hero.index)
            assertEquals(w / 2f, hero.center.x, 0.001f)
            assertEquals(OrbitLayout.HERO_DIAMETER, hero.diameter, 0.001f)
            assertTrue(slots.drop(1).all { it.diameter < hero.diameter })
        }
    }

    @Test
    fun secondAndThirdFlankTheTopBubbleAsMediumBubbles() {
        for (w in widths) {
            val slots = OrbitLayout.compute(10, w).slots
            assertTrue(slots[1].center.x < slots[0].center.x)
            assertTrue(slots[2].center.x > slots[0].center.x)
            assertEquals(slots[0].center.y, slots[1].center.y, 0.001f)
            assertEquals(slots[0].center.y, slots[2].center.y, 0.001f)
            assertEquals(slots[1].diameter, slots[2].diameter, 0.001f)
            assertEquals(w / 2f - slots[1].center.x, slots[2].center.x - w / 2f, 0.001f)
            assertTrue(slots[1].diameter > slots[3].diameter)
        }
    }

    @Test
    fun everySuggestionGetsExactlyOneSlotInOrder() {
        for (w in widths) for (count in 1..24) {
            val slots = OrbitLayout.compute(count, w).slots
            assertEquals("w=$w count=$count", (0 until count).toList(), slots.map { it.index })
        }
    }

    @Test
    fun gridHasFourPerRowReadingLeftToRightThenDown() {
        for (w in widths) {
            val slots = OrbitLayout.compute(20, w).slots.drop(3)
            val rows = slots.groupBy { it.center.y }.toSortedMap().values.toList()
            assertEquals(5, rows.size)
            rows.dropLast(1).forEach { assertEquals(4, it.size) }
            for (row in rows) {
                assertEquals(row.sortedBy { it.center.x }, row)
                assertTrue(row.map { it.diameter }.distinct().size == 1)
            }
            // Columns line up from row to row.
            val xs = rows.first().map { it.center.x }
            rows.forEach { row -> row.forEachIndexed { i, s -> assertEquals(xs[i], s.center.x, 0.001f) } }
            // Reading order: each row is below the last, and indexes only go up.
            assertEquals(slots.map { it.index }.sorted(), slots.map { it.index })
        }
    }

    @Test
    fun gridRowsGetSlightlySmallerDownToAMinimum() {
        val slots = OrbitLayout.compute(40, 360f).slots.drop(3)
        val diameters = slots.groupBy { it.center.y }.toSortedMap().values.map { it.first().diameter }
        assertEquals(diameters.sortedDescending(), diameters)
        assertTrue(diameters.first() > diameters.last())
        assertTrue(diameters.last() >= 52f)
    }

    @Test
    fun everythingStaysOnScreenHorizontallyAndBelowTheTop() {
        for (w in widths) {
            for (slot in OrbitLayout.compute(24, w).slots) {
                val box = slot.footprint
                assertTrue("left ${slot.index} w=$w", box.left >= 0f)
                assertTrue("right ${slot.index} w=$w", box.right <= w)
                assertTrue("top ${slot.index} w=$w", box.top >= 0f)
            }
        }
    }

    @Test
    fun noTwoBubblesOrNamesOverlap() {
        for (w in widths) for (count in listOf(3, 7, 14, 24)) for (scale in listOf(1f, 1.5f)) {
            val slots = OrbitLayout.compute(count, w, scale).slots
            for (i in slots.indices) for (j in i + 1 until slots.size) {
                assertTrue(
                    "overlap ${slots[i].index} vs ${slots[j].index} (w=$w, count=$count, scale=$scale)",
                    !slots[i].footprint.overlaps(slots[j].footprint),
                )
            }
        }
    }

    @Test
    fun everyBubbleShowsItsNameAndSubtitleRoom() {
        val slots = OrbitLayout.compute(20, 360f).slots
        assertTrue(slots.all { it.showSubtitle && it.labelHeight > 0f })
    }

    @Test
    fun nothingSitsOnTheHalo() {
        for (w in widths) {
            val result = OrbitLayout.compute(20, w)
            val radius = result.ringRadii.single()
            for (slot in result.slots.drop(1)) {
                val dx = slot.center.x - result.center.x
                val dy = slot.center.y - result.center.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                // The nearest edge of a bubble is outside the halo.
                assertTrue("slot ${slot.index} w=$w", distance - slot.diameter / 2f >= radius - 1f)
            }
        }
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
}
