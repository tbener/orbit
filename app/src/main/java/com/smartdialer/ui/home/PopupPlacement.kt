package com.smartdialer.ui.home

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/** Decides where the quick-actions card goes relative to the bubble it belongs to. */
object PopupPlacement {

    /**
     * Returns the top-left corner for a card of size [popup]. All values are in pixels.
     *
     * The card goes beside the bubble when it fits: to the right first, then to the left. When neither
     * side has room (the usual case on a phone) it goes below, then above. The result is always kept
     * inside [bounds] (the screen minus system bars, keyboard and margins); if the card is larger than
     * the space it is pinned to the top-left of [bounds].
     */
    fun place(anchor: IntRect, popup: IntSize, bounds: IntRect, gap: Int): IntOffset {
        fun clampX(x: Int) = x.coerceIn(bounds.left, max(bounds.left, bounds.right - popup.width))
        fun clampY(y: Int) = y.coerceIn(bounds.top, max(bounds.top, bounds.bottom - popup.height))
        val centeredX = clampX(anchor.center.x - popup.width / 2)
        val centeredY = clampY(anchor.center.y - popup.height / 2)

        val right = anchor.right + gap
        if (right + popup.width <= bounds.right) return IntOffset(right, centeredY)

        val left = anchor.left - gap - popup.width
        if (left >= bounds.left) return IntOffset(left, centeredY)

        val below = anchor.bottom + gap
        if (below + popup.height <= bounds.bottom) return IntOffset(centeredX, below)

        val above = anchor.top - gap - popup.height
        if (above >= bounds.top) return IntOffset(centeredX, above)

        return IntOffset(centeredX, centeredY)
    }
}
