package com.smartdialer.ui.home

/** What the user did to a bubble. */
enum class BubbleGesture { Tap, LongPress }

/** What a bubble gesture results in. */
enum class BubbleAction {
    /** Open the quick-actions card next to the bubble. */
    OpenQuickActions,

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

/** Tap and long-press both open the quick-actions card. */
val DefaultGestureMap = GestureMap(tap = BubbleAction.OpenQuickActions, longPress = BubbleAction.OpenQuickActions)
