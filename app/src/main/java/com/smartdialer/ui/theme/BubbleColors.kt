package com.smartdialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class BubbleColors(
    val container: Color,
    val content: Color,
    /** Neutral bubbles get an outline instead of a tinted fill. */
    val outlined: Boolean = false,
)

private val LightTints = listOf(
    BubbleColors(Color(0xFFCDE3FA), Color(0xFF1E4E8C)),
    BubbleColors(Color(0xFFF9D5D5), Color(0xFF9B2C2C)),
    BubbleColors(Color(0xFFCDEBD0), Color(0xFF1E6B2E)),
    BubbleColors(Color(0xFFF6DFA8), Color(0xFF7A5200)),
    BubbleColors(Color(0xFFDDD6FA), Color(0xFF4A3A9A)),
)

private val DarkTints = listOf(
    BubbleColors(Color(0xFF1F4170), Color(0xFFD3E4FA)),
    BubbleColors(Color(0xFF6B2A2A), Color(0xFFFAD0D0)),
    BubbleColors(Color(0xFF1F4D2A), Color(0xFFCDEBD0)),
    BubbleColors(Color(0xFF5C4300), Color(0xFFF6DFA8)),
    BubbleColors(Color(0xFF3D3475), Color(0xFFDDD6FA)),
)

/** Picks a stable color for a contact so a bubble keeps its color between refreshes. */
@Composable
fun bubbleColorsFor(contactId: String): BubbleColors {
    val tints = if (isSystemInDarkTheme()) DarkTints else LightTints
    val slot = abs(contactId.hashCode()) % (tints.size + 1)
    return if (slot < tints.size) {
        tints[slot]
    } else {
        BubbleColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, outlined = true)
    }
}
