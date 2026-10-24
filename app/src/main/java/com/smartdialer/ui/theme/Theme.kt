package com.smartdialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

private val LocalOrbitColors = compositionLocalOf { LightExtras }
private val LocalMotion = compositionLocalOf { Motion(reduced = false) }

/** Orbit's own design tokens, next to `MaterialTheme`. Colors and motion depend on the theme; the rest are constants. */
object Orbit {
    val colors: OrbitColors
        @Composable @ReadOnlyComposable get() = LocalOrbitColors.current

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val spacing = Spacing()
    val elevation = Elevation()
    val sizing = Sizing()
}

@Composable
fun OrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val motion = remember { Motion(Motion.systemReducedMotion(context)) }
    CompositionLocalProvider(
        LocalOrbitColors provides if (darkTheme) DarkExtras else LightExtras,
        LocalMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = OrbitTypography,
            shapes = OrbitShapes,
            content = content,
        )
    }
}

/** The two colors of a bubble: its fill and the color of the initials on it. */
data class BubbleColors(val container: Color, val content: Color)

/**
 * Picks the tone for a contact, the same one every time. The top two tiers use the stronger fill so the
 * people nearest the top stand out a little more; the hue itself never changes with rank.
 */
@Composable
fun bubbleColorsFor(contactId: String, strong: Boolean = false): BubbleColors {
    val tones = Orbit.colors.bubbleTones
    val tone = tones[abs(contactId.hashCode()) % tones.size]
    return BubbleColors(if (strong) tone.strong else tone.soft, tone.content)
}
