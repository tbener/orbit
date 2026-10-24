package com.smartdialer.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion tokens. Things arriving decelerate or spring, things leaving accelerate, and a leaving thing is
 * quicker than an arriving one. Motion explains a change (where something came from, where it went) or
 * gives the space a slow, quiet life (the drift and breathing of the orbit).
 *
 * When the user turns system animations off, [reduced] is true: every spec snaps and the ambient
 * drift and pulse are not run at all.
 */
@Immutable
class Motion(val reduced: Boolean) {
    val shortMillis = 120
    val mediumMillis = 220
    val longMillis = 340

    /** Delay between neighbouring bubbles on first appearance, and the most bubbles that get a delay. */
    val bubbleStaggerMillis = 30
    val bubbleStaggerLimit = 8

    /** Delay between neighbouring quick-action buttons springing out of (or back into) the bubble. */
    val arcStaggerMillis = 38

    /** One full cycle of the slow drift of the bubbles, and of the glow breathing. */
    val driftMillis = 18_000
    val breatheMillis = 5_200

    private val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For things appearing: bubbles arriving, a screen coming in. */
    fun <T> enter(durationMillis: Int = mediumMillis, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, delayMillis, decelerate)

    /** For things leaving: the scrim fading, a screen going out. */
    fun <T> exit(durationMillis: Int = shortMillis): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = accelerate)

    /** For things that stay but change: selection, a bubble lifting. */
    fun <T> move(durationMillis: Int = mediumMillis): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = standard)

    /** A lively spring with a little overshoot: the quick-action buttons popping out of the bubble. */
    fun <T> pop(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.58f, stiffness = 380f)

    /** A calmer spring: a bubble lifting forward. */
    fun <T> lift(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.78f, stiffness = 300f)

    companion object {
        fun systemReducedMotion(context: Context): Boolean = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}
