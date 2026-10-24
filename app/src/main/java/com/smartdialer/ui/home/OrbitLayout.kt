package com.smartdialer.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** One bubble's place on the orbit field. All values are in dp. */
data class OrbitSlot(
    /** Index into the suggestion list (0 is the top bubble in the middle). */
    val index: Int,
    /** 0 for the top bubble, 1 for the innermost ring, and so on. */
    val ring: Int,
    val center: Offset,
    val diameter: Float,
    /** The width of the name under the bubble. */
    val labelWidth: Float,
    /** The name, and for the inner rings a subtitle, hang below the bubble. */
    val labelHeight: Float,
    val showSubtitle: Boolean,
) {
    /** Everything the bubble occupies: the circle and its label. */
    val footprint: Rect
        get() {
            val half = maxOf(diameter, labelWidth) / 2f
            return Rect(center.x - half, center.y - diameter / 2f, center.x + half, center.y + diameter / 2f + labelHeight)
        }
}

data class OrbitLayoutResult(
    val slots: List<OrbitSlot>,
    /** The center of the top bubble, which is also the center of every ring. */
    val center: Offset,
    /** Radii of the rings that hold bubbles, innermost first, plus one more to hint at the next. */
    val ringRadii: List<Float>,
    val contentHeight: Float,
)

/**
 * Places the suggestions on concentric rings around the top bubble. Pure and deterministic.
 *
 * The top bubble sits in the middle. The next ones go on the innermost ring, to the left and right of it
 * first, then wherever there is room. When a ring is full the rest move out to the next ring, where the
 * bubbles are a little smaller. A bubble is only placed where it (and its name) stays on screen and does
 * not overlap another bubble or name. Rings further out are only visible as arcs, because the screen is
 * narrower than they are wide.
 */
object OrbitLayout {
    const val HERO_DIAMETER = 132f
    private const val HERO_LABEL_HEIGHT = 46f
    private const val TOP_MARGIN = 10f
    private const val SIDE_MARGIN = 8f
    private const val GAP = 6f
    private const val ANGLE_STEP = 5
    private const val BOTTOM_ROOM = 150f
    private const val MAX_RINGS = 40

    /** Where the top bubble's center is, from the top of the field. Room above for the first ring. */
    const val HERO_CENTER_Y = 214f

    /** The rings are ellipses, this much taller than wide, so they use the room above and below the top bubble. */
    const val RING_STRETCH = 1.35f

    private class RingSpec(val radius: Float, val diameter: Float, val labelHeight: Float, val subtitle: Boolean)

    /** Ring sizes. [labelScale] grows the room under each bubble for names when the font is large. */
    private fun ringSpec(ring: Int, labelScale: Float): RingSpec = when (ring) {
        1 -> RingSpec(radius = 118f, diameter = 76f, labelHeight = 40f * labelScale, subtitle = true)
        else -> {
            val diameter = (66f - 5f * (ring - 2)).coerceAtLeast(46f)
            // Further out each ring is one bubble, one name and a gap beyond the previous one.
            val previous = ringSpec(ring - 1, labelScale)
            val radius = (previous.radius + previous.diameter / 2f + previous.labelHeight + diameter / 2f + 6f - 20f)
            RingSpec(radius, diameter, labelHeight = 20f * labelScale, subtitle = false)
        }
    }

    fun compute(count: Int, width: Float, labelScale: Float = 1f): OrbitLayoutResult {
        val center = Offset(width / 2f, HERO_CENTER_Y)
        if (count <= 0) return OrbitLayoutResult(emptyList(), center, emptyList(), 0f)

        val hero = OrbitSlot(0, 0, center, HERO_DIAMETER, HERO_DIAMETER, HERO_LABEL_HEIGHT * labelScale, showSubtitle = true)
        val slots = mutableListOf(hero)
        var ring = 1

        for (index in 1 until count) {
            var placed: OrbitSlot? = null
            while (placed == null && ring <= MAX_RINGS) {
                placed = place(index, ring, center, width, slots, labelScale)
                if (placed == null) ring++
            }
            slots += placed ?: break
        }

        val usedRings = slots.maxOf { it.ring }
        val radii = (1..minOf(usedRings + 1, MAX_RINGS)).map { ringSpec(it, labelScale).radius }
        val bottom = slots.maxOf { it.footprint.bottom }
        return OrbitLayoutResult(slots, center, radii, bottom + BOTTOM_ROOM)
    }

    private fun place(index: Int, ring: Int, center: Offset, width: Float, placed: List<OrbitSlot>, labelScale: Float): OrbitSlot? {
        val spec = ringSpec(ring, labelScale)
        val labelWidth = if (spec.subtitle) spec.diameter + 12f else spec.diameter + 8f
        for (degrees in candidateAngles(ring)) {
            val radians = degrees * PI / 180.0
            val slot = OrbitSlot(
                index = index,
                ring = ring,
                center = Offset(center.x + spec.radius * sin(radians).toFloat(), center.y + spec.radius * RING_STRETCH * cos(radians).toFloat()),
                diameter = spec.diameter,
                labelWidth = labelWidth,
                labelHeight = spec.labelHeight,
                showSubtitle = spec.subtitle,
            )
            val box = slot.footprint
            val fits = box.left >= SIDE_MARGIN && box.right <= width - SIDE_MARGIN && box.top >= TOP_MARGIN
            if (fits && placed.none { it.footprint.inflate(GAP / 2f).overlaps(box.inflate(GAP / 2f)) }) return slot
        }
        return null
    }

    /**
     * Angles to try on a ring, in order of preference, in degrees measured from straight down (0): -90 is
     * to the left of the top bubble and +90 to its right, 180 is straight up. The innermost ring prefers
     * the two sides; further rings prefer straight below, where there is room to scroll.
     */
    private fun candidateAngles(ring: Int): List<Int> {
        val all = (-180 until 180 step ANGLE_STEP).toList() + 180
        val ordered = if (ring == 1) {
            all.sortedWith(compareBy({ abs(abs(it) - 90) }, { if (it < 0) 0 else 1 }))
        } else {
            all.sortedWith(compareBy({ abs(it) }, { if (it < 0) 0 else 1 }))
        }
        return ordered.distinct().filter { it != 180 || ring == 1 }
    }
}
