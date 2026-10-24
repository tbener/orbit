package com.smartdialer.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where the quick-action buttons and the caption go around a bubble. Values are in the caller's units (px or dp). */
data class QuickLayoutResult(
    /** The center of each button, in action order. */
    val buttonCenters: List<Offset>,
    /** The top-left corner of the caption pill. */
    val captionTopLeft: Offset,
    /** True when no arc fitted and the buttons were put in a straight row instead. */
    val fellBack: Boolean,
    /** Where each button's label goes (beyond its button along the arc; under it in a row). */
    val labelRects: List<Rect> = emptyList(),
)

/**
 * Decides where the quick-action buttons go. Pure and deterministic.
 *
 * The buttons sit on an arc around the bubble. The arc prefers to open upward, above the bubble. It
 * turns to the side or points downward only when there is no room above, and a candidate is rejected
 * if any button (with its label) would leave [bounds], or cover the bubble's name ([nameRect]). The
 * arc is tried at a few radii; if nothing fits, the buttons go in a row so nothing is ever off screen.
 */
object QuickLayout {

    fun place(
        bubbleCenter: Offset,
        bubbleRadius: Float,
        buttonCount: Int,
        buttonSize: Float,
        labelHeight: Float,
        nameRect: Rect,
        captionSize: Size,
        bounds: Rect,
        gap: Float,
    ): QuickLayoutResult {
        val footprintWidth = buttonSize + gap
        val radii = listOf(0f, 14f, 28f, 44f, 70f, 100f, 140f).map { bubbleRadius + buttonSize / 2f + gap * 3f + it }

        fun footprint(center: Offset) = buttonFootprint(center, bubbleCenter, buttonSize, footprintWidth, labelHeight)

        fun valid(centers: List<Offset>): Boolean = centers.all { c ->
            val box = footprint(c)
            box.left >= bounds.left && box.right <= bounds.right && box.top >= bounds.top && box.bottom <= bounds.bottom &&
                !box.overlaps(nameRect) && distanceToRect(bubbleCenter, box) >= bubbleRadius + 2f
        }

        var buttons: List<Offset>? = null
        outer@ for (radius in radii) {
            val step = 2.0 * asin(min(1.0, (footprintWidth / 2.0 + 1.0) / radius))
            for (degrees in preferredDirections(bubbleCenter, bounds)) {
                val middle = degrees * PI / 180.0
                val centers = List(buttonCount) { i ->
                    val angle = middle + (i - (buttonCount - 1) / 2.0) * step
                    Offset(bubbleCenter.x + (radius * cos(angle)).toFloat(), bubbleCenter.y + (radius * sin(angle)).toFloat())
                }
                if (valid(centers)) {
                    buttons = centers
                    break@outer
                }
            }
        }

        val fellBack = buttons == null
        val finalButtons = buttons ?: rowFallback(bubbleCenter, bubbleRadius, buttonCount, buttonSize, labelHeight, gap, nameRect, bounds)
        val labels = finalButtons.map { c ->
            if (fellBack) {
                // In a row the label sits under its button.
                Rect(c.x - footprintWidth / 2f, c.y + buttonSize / 2f, c.x + footprintWidth / 2f, c.y + buttonSize / 2f + labelHeight)
            } else {
                labelRect(c, bubbleCenter, buttonSize, footprintWidth, labelHeight)
            }
        }
        val caption = placeCaption(bubbleCenter, bubbleRadius, finalButtons.map(::footprint), nameRect, captionSize, bounds, gap)
        return QuickLayoutResult(finalButtons, caption, fellBack, labels)
    }

    /**
     * What a button occupies: its circle and its label, which sits on the outer side (above the button when
     * the button is above the bubble, below it otherwise) so it never lands between the button and the bubble.
     */
    fun buttonFootprint(center: Offset, bubbleCenter: Offset, size: Float, width: Float, labelHeight: Float): Rect {
        val button = Rect(center.x - size / 2f, center.y - size / 2f, center.x + size / 2f, center.y + size / 2f)
        val label = labelRect(center, bubbleCenter, size, width, labelHeight)
        return Rect(min(button.left, label.left), min(button.top, label.top), max(button.right, label.right), max(button.bottom, label.bottom))
    }

    /**
     * Where a button's label goes: pushed outward from the bubble along the direction from the bubble's center
     * through the button, so a label sits beyond its button and never between the button and the bubble.
     */
    fun labelRect(center: Offset, bubbleCenter: Offset, size: Float, width: Float, height: Float): Rect {
        var dx = center.x - bubbleCenter.x
        var dy = center.y - bubbleCenter.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 1f) { dx = 0f; dy = -1f } else { dx /= length; dy /= length }
        val extent = abs(dx) * width / 2f + abs(dy) * height / 2f
        val distance = size / 2f + size * 0.06f + extent
        val cx = center.x + dx * distance
        val cy = center.y + dy * distance
        return Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
    }

    private fun distanceToRect(p: Offset, r: Rect): Float {
        val dx = max(max(r.left - p.x, 0f), p.x - r.right)
        val dy = max(max(r.top - p.y, 0f), p.y - r.bottom)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Directions to try for the middle of the arc, in degrees, 0 = right and -90 = up on screen. Starts with
     * straight up and widens, preferring the side that points toward the middle of the screen.
     */
    private fun preferredDirections(from: Offset, bounds: Rect): List<Double> {
        val towardCenter = if (from.x <= bounds.center.x) 1 else -1
        val offsets = (0..180 step 5).flatMap { listOf(it, -it) }.distinct()
        return offsets
            .sortedWith(compareBy<Int>({ abs(it) }, { if (it * towardCenter >= 0) 0 else 1 }))
            .map { -90.0 + it * towardCenter }
    }

    /**
     * Where a floating panel (the instruction field) goes: below [anchor] if it fits, otherwise above it,
     * otherwise pinned inside [bounds]. [anchor] is the bubble together with its name.
     */
    fun placeBelowOrAbove(anchor: Rect, size: Size, bounds: Rect, gap: Float): Offset {
        val x = (anchor.center.x - size.width / 2f).coerceIn(bounds.left, max(bounds.left, bounds.right - size.width))
        val below = anchor.bottom + gap
        if (below + size.height <= bounds.bottom) return Offset(x, below)
        val above = anchor.top - gap - size.height
        if (above >= bounds.top) return Offset(x, above)
        return Offset(x, below.coerceIn(bounds.top, max(bounds.top, bounds.bottom - size.height)))
    }

    private fun rowFallback(
        bubbleCenter: Offset,
        bubbleRadius: Float,
        count: Int,
        size: Float,
        labelHeight: Float,
        gap: Float,
        nameRect: Rect,
        bounds: Rect,
    ): List<Offset> {
        // Each button owns a slot as wide as its footprint, so neither the button nor its label leaves the bounds.
        val slotWidth = size + gap
        val rowWidth = count * slotWidth
        val left = (bubbleCenter.x - rowWidth / 2f).coerceIn(bounds.left, max(bounds.left, bounds.right - rowWidth))
        val rowHeight = size + labelHeight
        val aboveTop = bubbleCenter.y - bubbleRadius - gap - rowHeight
        val top = if (aboveTop >= bounds.top) aboveTop else min(nameRect.bottom + gap, max(bounds.top, bounds.bottom - rowHeight))
        return List(count) { Offset(left + slotWidth / 2f + it * slotWidth, top + size / 2f) }
    }

    private fun placeCaption(
        bubbleCenter: Offset,
        bubbleRadius: Float,
        buttonBoxes: List<Rect>,
        nameRect: Rect,
        size: Size,
        bounds: Rect,
        gap: Float,
    ): Offset {
        fun clampX(x: Float) = x.coerceIn(bounds.left, max(bounds.left, bounds.right - size.width))
        fun clampY(y: Float) = y.coerceIn(bounds.top, max(bounds.top, bounds.bottom - size.height))
        val x = clampX(bubbleCenter.x - size.width / 2f)
        val below = Offset(x, nameRect.bottom + gap)
        val aboveY = minOf(buttonBoxes.minOfOrNull { it.top } ?: Float.MAX_VALUE, bubbleCenter.y - bubbleRadius) - gap - size.height
        val above = Offset(x, aboveY)

        fun free(topLeft: Offset): Boolean {
            val box = Rect(topLeft, size)
            return box.top >= bounds.top && box.bottom <= bounds.bottom && buttonBoxes.none { it.overlaps(box) } && !box.overlaps(nameRect)
        }
        return listOf(below, above).firstOrNull(::free) ?: Offset(x, clampY(below.y))
    }
}
