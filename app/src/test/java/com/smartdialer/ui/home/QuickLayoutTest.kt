package com.smartdialer.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLayoutTest {

    private val bounds = Rect(8f, 40f, 352f, 760f)
    private val buttonSize = 52f
    private val labelHeight = 16f
    private val gap = 8f
    private val caption = Size(240f, 52f)

    /** A bubble of the given radius whose center is at [x], [y], with its name just under it. */
    private fun layout(x: Float, y: Float, radius: Float = 38f, count: Int = 5): QuickLayoutResult {
        val center = Offset(x, y)
        val nameRect = Rect(x - 44f, y + radius + 2f, x + 44f, y + radius + 40f)
        return QuickLayout.place(center, radius, count, buttonSize, labelHeight, nameRect, caption, bounds, gap)
    }

    private fun footprint(c: Offset, bubble: Offset) =
        QuickLayout.buttonFootprint(c, bubble, buttonSize, buttonSize + gap, labelHeight)

    private fun assertEverythingFits(x: Float, y: Float, radius: Float = 38f, count: Int = 5) {
        val result = layout(x, y, radius, count)
        val nameRect = Rect(x - 44f, y + radius + 2f, x + 44f, y + radius + 40f)
        result.buttonCenters.forEachIndexed { i, c ->
            val box = footprint(c, Offset(x, y))
            assertTrue("button $i left at ($x,$y)", box.left >= bounds.left - 0.01f)
            assertTrue("button $i right at ($x,$y)", box.right <= bounds.right + 0.01f)
            assertTrue("button $i top at ($x,$y)", box.top >= bounds.top - 0.01f)
            assertTrue("button $i bottom at ($x,$y)", box.bottom <= bounds.bottom + 0.01f)
            assertFalse("button $i covers the name at ($x,$y)", box.overlaps(nameRect))
            assertTrue("button $i covers the bubble", (c - Offset(x, y)).getDistance() >= radius + buttonSize / 2f)
            val nearestX = Offset(x, y).x.coerceIn(box.left, box.right)
            val nearestY = Offset(x, y).y.coerceIn(box.top, box.bottom)
            assertTrue("label of button $i touches the bubble at ($x,$y)", (Offset(nearestX, nearestY) - Offset(x, y)).getDistance() >= radius)
        }
        val captionBox = Rect(result.captionTopLeft, caption)
        assertTrue("caption inside at ($x,$y)", captionBox.left >= bounds.left - 0.01f && captionBox.right <= bounds.right + 0.01f)
        assertTrue("caption top at ($x,$y)", captionBox.top >= bounds.top - 0.01f && captionBox.bottom <= bounds.bottom + 0.01f)
        assertFalse("caption covers the name at ($x,$y)", captionBox.overlaps(nameRect))
    }

    @Test
    fun aBubbleInTheMiddleGetsAnArcAboveIt() {
        val result = layout(180f, 400f)
        assertFalse(result.fellBack)
        assertTrue(result.buttonCenters.all { it.y <= 410f })
        assertEverythingFits(180f, 400f)
    }

    @Test
    fun aBubbleNearTheTopStillGetsAllButtonsOnScreenAndKeepsItsNameClear() {
        assertEverythingFits(180f, 150f, radius = 66f)
        assertEverythingFits(180f, 130f, radius = 66f)
    }

    @Test
    fun aBubbleNearTheLeftEdgeFits() {
        assertEverythingFits(45f, 400f)
        assertEverythingFits(45f, 120f)
    }

    @Test
    fun aBubbleNearTheRightEdgeFits() {
        assertEverythingFits(315f, 400f)
        assertEverythingFits(315f, 700f)
    }

    @Test
    fun aBubbleNearTheBottomFits() {
        assertEverythingFits(180f, 690f)
        assertEverythingFits(60f, 700f)
    }

    @Test
    fun nothingEverLeavesTheScreenOrCoversAName() {
        for (x in 40..320 step 40) for (y in 130..700 step 45) {
            assertEverythingFits(x.toFloat(), y.toFloat())
            assertEverythingFits(x.toFloat(), y.toFloat(), radius = 28f)
        }
    }

    @Test
    fun theButtonCountDoesNotMatter() {
        for (count in 1..7) assertEverythingFits(100f, 300f, count = count)
    }

    @Test
    fun theLayoutIsDeterministic() {
        assertTrue(layout(150f, 321f) == layout(150f, 321f))
    }

    @Test
    fun anImpossiblySmallSpaceFallsBackToARowInsideTheBounds() {
        val tight = Rect(0f, 0f, 330f, 150f)
        val center = Offset(165f, 60f)
        val nameRect = Rect(120f, 100f, 210f, 140f)
        val result = QuickLayout.place(center, 38f, 5, buttonSize, labelHeight, nameRect, Size(200f, 40f), tight, gap)
        assertTrue(result.fellBack)
        result.buttonCenters.forEach { c ->
            assertTrue(c.x - buttonSize / 2f >= tight.left && c.x + buttonSize / 2f <= tight.right)
        }
    }

    @Test
    fun theInstructionFieldGoesBelowABubbleWhenThereIsRoom() {
        val anchor = Rect(140f, 200f, 220f, 300f)
        val at = QuickLayout.placeBelowOrAbove(anchor, Size(300f, 60f), bounds, gap)
        assertTrue(at.y >= anchor.bottom)
        assertTrue(at.x >= bounds.left && at.x + 300f <= bounds.right)
    }

    @Test
    fun theInstructionFieldGoesAboveABubbleNearTheBottom() {
        val anchor = Rect(140f, 640f, 220f, 740f)
        val at = QuickLayout.placeBelowOrAbove(anchor, Size(300f, 60f), bounds, gap)
        assertTrue(at.y + 60f <= anchor.top)
    }
}
