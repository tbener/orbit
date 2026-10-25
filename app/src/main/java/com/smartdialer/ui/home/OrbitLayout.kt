package com.smartdialer.ui.home

import com.smartdialer.data.LayoutMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** One bubble's place on the field. All values are in dp. */
data class OrbitSlot(
    /** Index into the suggestion list (0 is the top bubble in the middle of the first row). */
    val index: Int,
    /** Size tier: 0 for the top bubble, 1 for #2 and #3, then 2, 3, ... for each grid row, a little smaller each time. */
    val ring: Int,
    val center: Offset,
    val diameter: Float,
    /** The width of the name under the bubble. */
    val labelWidth: Float,
    /** The name hangs below the bubble. */
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
    /** The center of the top bubble. */
    val center: Offset,
    /** Radii of the faint halo rings behind the top bubble; empty when there is nothing to show. */
    val ringRadii: List<Float>,
    val contentHeight: Float,
)

/**
 * Places the suggestions in rows. Pure and deterministic.
 *
 * The first row holds the top three: the top bubble largest and in the middle, #2 on its left and #3 on its
 * right, both medium. After that the bubbles sit in an aligned grid of [COLUMNS] per row, left to right and
 * top to bottom, each row a little smaller than the one above (down to a minimum). Every bubble has its name
 * under it, with the subtitle below that, both centered on the bubble. Nothing is placed on a ring;
 * the only rings are a faint halo hugging the top bubble.
 */
object OrbitLayout {
    const val HERO_DIAMETER = 136f
    const val COLUMNS = 4

    /** Where the top bubble's center is, from the top of the field. */
    const val HERO_CENTER_Y = 94f

    private const val MEDIUM_DIAMETER = 92f
    private const val HERO_LABEL_HEIGHT = 34f
    private const val SIDE_MARGIN = 8f
    private const val GAP = 6f
    private const val ROW_GAP = 16f
    private const val FIRST_GRID_DIAMETER = 72f
    private const val GRID_STEP = 4f
    private const val MIN_GRID_DIAMETER = 52f
    private const val BOTTOM_ROOM = 150f
    private const val HALO_GAP = 6f

    /** Room under a bubble for its name and subtitle; grows with the font so the text stays inside. */
    private fun labelHeight(tier: Int, labelScale: Float): Float = when (tier) {
        0 -> HERO_LABEL_HEIGHT * labelScale
        1 -> 26f * labelScale
        else -> 24f * labelScale
    }

    /** The diameter of a grid bubble in the given grid row (0 is the first row below the top three). */
    private fun gridDiameter(row: Int, cellWidth: Float): Float =
        (FIRST_GRID_DIAMETER - GRID_STEP * row).coerceAtLeast(MIN_GRID_DIAMETER).coerceAtMost(cellWidth - GAP)

    /**
     * Places the suggestions for the chosen [mode]. [LayoutMode.Spotlight] is the layout described above; the grid
     * modes put equal bubbles in rows of 3 or 4 from the left. In every mode slot index + 1 is the rank.
     */
    fun compute(count: Int, width: Float, labelScale: Float = 1f, mode: LayoutMode = LayoutMode.Spotlight): OrbitLayoutResult =
        when (mode) {
            LayoutMode.Spotlight -> spotlight(count, width, labelScale)
            LayoutMode.Grid3 -> grid(count, width, labelScale, columns = 3, maxDiameter = 92f, tier = 1, gap = 34f)
            LayoutMode.Grid4 -> grid(count, width, labelScale, columns = 4, maxDiameter = 88f, tier = 2, gap = 14f)
        }

    /** Equal bubbles in rows of [columns], sized to the screen width, each with its name underneath. */
    private fun grid(count: Int, width: Float, labelScale: Float, columns: Int, maxDiameter: Float, tier: Int, gap: Float): OrbitLayoutResult {
        val margin = 12f
        val cell = (width - 2f * margin) / columns
        val diameter = (cell - gap).coerceAtMost(maxDiameter).coerceAtLeast(40f)
        val labelHeight = labelHeight(2, labelScale)
        val top = 16f
        val slots = (0 until count).map { index ->
            val row = index / columns
            val column = index % columns
            OrbitSlot(
                index = index,
                ring = tier,
                center = Offset(margin + cell * (column + 0.5f), top + diameter / 2f + row * (diameter + labelHeight + ROW_GAP)),
                diameter = diameter,
                labelWidth = cell - 4f,
                labelHeight = labelHeight,
                showSubtitle = true,
            )
        }
        val center = slots.firstOrNull()?.center ?: Offset(width / 2f, top + diameter / 2f)
        val bottom = slots.maxOfOrNull { it.footprint.bottom } ?: 0f
        return OrbitLayoutResult(slots, center, emptyList(), if (slots.isEmpty()) 0f else bottom + BOTTOM_ROOM)
    }

    private fun spotlight(count: Int, width: Float, labelScale: Float): OrbitLayoutResult {
        val center = Offset(width / 2f, HERO_CENTER_Y)
        if (count <= 0) return OrbitLayoutResult(emptyList(), center, emptyList(), 0f)

        val slots = mutableListOf<OrbitSlot>()
        val heroLabelHeight = labelHeight(0, labelScale)
        slots += OrbitSlot(0, 0, center, HERO_DIAMETER, HERO_DIAMETER, heroLabelHeight, showSubtitle = true)

        // #2 and #3 are centered in the space between the screen edge and the top bubble.
        val sideRegion = width / 2f - HERO_DIAMETER / 2f - SIDE_MARGIN
        val sideDiameter = MEDIUM_DIAMETER.coerceAtMost(sideRegion - GAP)
        val sideLabelWidth = sideRegion + HALO_GAP
        val sideCenterOffset = HERO_DIAMETER / 2f + HALO_GAP + sideRegion / 2f
        for (index in 1..minOf(2, count - 1)) {
            val direction = if (index == 1) -1f else 1f
            slots += OrbitSlot(
                index = index,
                ring = 1,
                center = Offset(center.x + direction * sideCenterOffset, center.y),
                diameter = sideDiameter,
                labelWidth = sideLabelWidth - GAP,
                labelHeight = labelHeight(1, labelScale),
                showSubtitle = true,
            )
        }

        // The grid starts below the tallest thing in the first row.
        var rowTop = slots.maxOf { it.footprint.bottom } + ROW_GAP
        val cellWidth = (width - 2f * SIDE_MARGIN) / COLUMNS
        var index = 3
        var row = 0
        while (index < count) {
            val diameter = gridDiameter(row, cellWidth)
            val tier = 2 + row
            val height = labelHeight(tier, labelScale)
            for (column in 0 until COLUMNS) {
                if (index >= count) break
                slots += OrbitSlot(
                    index = index,
                    ring = tier,
                    center = Offset(SIDE_MARGIN + cellWidth * (column + 0.5f), rowTop + diameter / 2f),
                    diameter = diameter,
                    labelWidth = cellWidth - GAP / 2f,
                    labelHeight = height,
                    showSubtitle = true,
                )
                index++
            }
            rowTop += diameter + height + ROW_GAP
            row++
        }

        val halo = listOf(HERO_DIAMETER / 2f + HALO_GAP)
        val bottom = slots.maxOf { it.footprint.bottom }
        return OrbitLayoutResult(slots, center, halo, bottom + BOTTOM_ROOM)
    }
}
