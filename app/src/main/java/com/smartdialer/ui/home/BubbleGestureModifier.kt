package com.smartdialer.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/** What a drag of a bubble reports. [onMove] gets where the finger is now, relative to where it went down. */
class BubbleDragCallbacks(
    val enabled: () -> Boolean,
    val onStart: () -> Unit,
    val onMove: (Offset) -> Unit,
    /** [moved] is false when the hold was released without moving. */
    val onEnd: (moved: Boolean) -> Unit,
)

/**
 * Tap, or press and hold (a light haptic): moving the finger then drags, releasing in place is a long press.
 *
 * This must sit on something that does NOT move while it is dragged (the field puts it on a still overlay over the
 * bubble's original slot), because positions are measured relative to the element; the dragged bubble itself is
 * drawn somewhere else. Movement scrolls the list instead when it starts before the hold is complete.
 */
@Composable
fun Modifier.bubbleGestures(
    onPressedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onHoldRelease: () -> Unit,
    drag: () -> BubbleDragCallbacks?,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val pressedChange by rememberUpdatedState(onPressedChange)
    val tap by rememberUpdatedState(onTap)
    val holdRelease by rememberUpdatedState(onHoldRelease)
    val dragProvider by rememberUpdatedState(drag)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            pressedChange(true)
            var completed = false
            var up: PointerInputChange? = null
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                up = waitForUpOrCancellation()
                completed = true
            }
            if (completed) {
                pressedChange(false)
                val released = up
                if (released != null && (released.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                    released.consume()
                    tap()
                }
                return@awaitEachGesture
            }
            // Held long enough.
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val callbacks = dragProvider()?.takeIf { it.enabled() }
            callbacks?.onStart?.invoke()
            var moved = false
            val finished = drag(down.id) { change ->
                change.consume()
                val offset = change.position - down.position
                if (offset.getDistance() > viewConfiguration.touchSlop) moved = true
                if (moved) callbacks?.onMove?.invoke(offset)
            }
            pressedChange(false)
            if (callbacks != null) callbacks.onEnd(finished && moved)
            if (finished && !moved) holdRelease()
        }
    }
}
