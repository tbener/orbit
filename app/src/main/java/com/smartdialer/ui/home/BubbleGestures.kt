package com.smartdialer.ui.home

/** What the user did to a bubble. */
enum class BubbleGesture { Tap, LongPress }

/** What a bubble gesture results in. */
enum class BubbleAction {
    /** Open the quick-actions card next to the bubble. */
    OpenQuickActions,

    /** Pin the contact to its current slot, or unpin it when it is already pinned. */
    TogglePin,

    /** Call the contact right away. */
    Call,
}

/**
 * The single place that says which gesture does what. To make this configurable later, build a
 * [GestureMap] from a setting and pass it to the home screen.
 */
data class GestureMap(val tap: BubbleAction, val longPress: BubbleAction) {
    fun actionFor(gesture: BubbleGesture): BubbleAction = when (gesture) {
        BubbleGesture.Tap -> tap
        BubbleGesture.LongPress -> longPress
    }
}

/** Tap opens the quick actions; long-press pins (or unpins) the contact at its current slot. */
val DefaultGestureMap = GestureMap(tap = BubbleAction.OpenQuickActions, longPress = BubbleAction.TogglePin)
