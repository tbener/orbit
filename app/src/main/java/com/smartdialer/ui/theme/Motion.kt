package com.smartdialer.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion tokens. Things arriving decelerate, things leaving accelerate, and a leaving thing is quicker
 * than an arriving one. Motion is only used to explain a change (where something came from, where it
 * went); there is no decorative looping animation except the progress indicator.
 *
 * When the user turns system animations off, [reduced] is true and every spec snaps.
 */
@Immutable
class Motion(val reduced: Boolean) {
    val shortMillis = 120
    val mediumMillis = 200
    val longMillis = 320

    /** Delay between neighbouring bubbles on first appearance, and the most bubbles that get a delay. */
    val bubbleStaggerMillis = 30
    val bubbleStaggerLimit = 8

    private val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things appearing: the popup opening, bubbles arriving, a screen coming in. */
    fun <T> enter(durationMillis: Int = mediumMillis, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, delayMillis, decelerate)

    /** For things leaving: the popup closing, a screen going out. */
    fun <T> exit(durationMillis: Int = shortMillis): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = accelerate)

    /** For things that stay but change: selection, a bubble moving. */
    fun <T> move(durationMillis: Int = mediumMillis): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = standard)

    companion object {
        fun systemReducedMotion(context: Context): Boolean = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}
