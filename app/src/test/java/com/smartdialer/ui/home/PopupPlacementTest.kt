package com.smartdialer.ui.home

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPlacementTest {

    private val screen = IntRect(16, 48, 1064, 2200)
    private val popup = IntSize(300, 400)
    private val gap = 12

    private fun place(anchor: IntRect, bounds: IntRect = screen, size: IntSize = popup) =
        PopupPlacement.place(anchor, size, bounds, gap)

    private fun assertInside(offset: IntOffset, size: IntSize = popup, bounds: IntRect = screen) {
        assertTrue("left", offset.x >= bounds.left)
        assertTrue("top", offset.y >= bounds.top)
        assertTrue("right", offset.x + size.width <= bounds.right)
        assertTrue("bottom", offset.y + size.height <= bounds.bottom)
    }

    @Test
    fun prefersTheRightSideWhenItFits() {
        val anchor = IntRect(100, 800, 220, 920)
        val p = place(anchor)
        assertEquals(anchor.right + gap, p.x)
        assertInside(p)
    }

    @Test
    fun flipsToTheLeftNearTheRightEdge() {
        val anchor = IntRect(900, 800, 1020, 920)
        val p = place(anchor)
        assertEquals(anchor.left - gap - popup.width, p.x)
        assertInside(p)
    }

    @Test
    fun goesBelowWhenNeitherSideHasRoom() {
        val narrow = IntRect(0, 0, 400, 2200)
        val anchor = IntRect(140, 600, 260, 720)
        val p = place(anchor, narrow, IntSize(380, 300))
        assertEquals(anchor.bottom + gap, p.y)
        assertInside(p, IntSize(380, 300), narrow)
    }

    @Test
    fun goesAboveNearTheBottomEdge() {
        val narrow = IntRect(0, 0, 400, 2200)
        val anchor = IntRect(140, 2000, 260, 2120)
        val size = IntSize(380, 300)
        val p = place(anchor, narrow, size)
        assertEquals(anchor.top - gap - size.height, p.y)
        assertInside(p, size, narrow)
    }

    @Test
    fun sideCardIsKeptInsideVerticallyNearTheTop() {
        val anchor = IntRect(100, 60, 220, 180)
        val p = place(anchor)
        assertEquals(screen.top, p.y)
        assertInside(p)
    }

    @Test
    fun sideCardIsKeptInsideVerticallyNearTheBottom() {
        val anchor = IntRect(100, 2100, 220, 2200)
        val p = place(anchor)
        assertInside(p)
    }

    @Test
    fun neverLeavesTheScreenForAnyAnchorPosition() {
        for (x in 0..1000 step 90) for (y in 40..2100 step 130) {
            val anchor = IntRect(x, y, x + 100, y + 100)
            assertInside(place(anchor))
        }
    }

    @Test
    fun cardLargerThanTheSpaceIsPinnedToTheTopLeft() {
        val tiny = IntRect(0, 0, 200, 200)
        val p = place(IntRect(50, 50, 100, 100), tiny, IntSize(300, 300))
        assertEquals(IntOffset(0, 0), p)
    }
}
