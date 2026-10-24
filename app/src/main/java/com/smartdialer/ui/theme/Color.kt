package com.smartdialer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One tone of the people palette: a gradient from [top] to [bottom], and the initials color legible on both. */
@Immutable
class BubbleTone(val top: Color, val bottom: Color, val content: Color)

/** A solid-looking gradient for a round action button; the glyph on it is white (contrast at least 3:1). */
@Immutable
class ActionStyle(val top: Color, val bottom: Color)

/** Everything the Material 3 scheme has no role for. Read it with `Orbit.colors`. */
@Immutable
class OrbitColors(
    val isDark: Boolean,
    /** The people palette. A contact keeps the same tone for good; priority is shown by size and glow. */
    val bubbleTones: List<BubbleTone>,
    /** The signature accent: a solid color for text and icons, and a gradient pair for fills. */
    val accent: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val onAccent: Color,
    /** Free-text rules ("soft", read by the LLM) and typed rules ("solid", enforced by code). */
    val softAccent: Color,
    val solidAccent: Color,
    val softFillStart: Color,
    val softFillEnd: Color,
    val solidFill: Color,
    /** The screen background gradient and the glow behind the top bubble. */
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val heroGlow: Color,
    /** Soft pastel blobs behind the light theme. Empty in dark, where faint stars are drawn instead. */
    val blobs: List<Color>,
    /** Frosted-glass surfaces. */
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    /** Missed-call badge and alert text. */
    val alert: Color,
    val onAlert: Color,
    val alertText: Color,
    /** Dims and darkens the screen behind the quick actions. */
    val scrim: Color,
    /** The orbit rings. */
    val ring: Color,
    /** One color per quick action. Green is reserved for WhatsApp. The AI sparkle has its own violet-to-cyan pair. */
    val actionCall: ActionStyle,
    val actionWhatsApp: ActionStyle,
    val actionContact: ActionStyle,
    val actionDetails: ActionStyle,
    val actionAi: ActionStyle,
)

private val ActionCall = ActionStyle(Color(0xFF1E88E5), Color(0xFF1565C0))
private val ActionWhatsApp = ActionStyle(Color(0xFF1DA851), Color(0xFF148A41)) // WhatsApp green, darkened to reach 3:1 with a white glyph
private val ActionContact = ActionStyle(Color(0xFF8E5CF0), Color(0xFF6D3FD6))
private val ActionDetails = ActionStyle(Color(0xFFE06A22), Color(0xFFC4501A))
private val ActionAi = ActionStyle(Color(0xFF7C5CFF), Color(0xFF0E8FC2))

// Dark is the primary design: deep ink with a hint of navy, bright glossy orbs, one aqua-to-azure accent.
// Light is its own design: soft off-white with pastel light, pastel orbs with deep initials.
// Every text pair was checked for at least 4.5:1 contrast (see docs/DESIGN.md).

private val Ink = Color(0xFF0B1020)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Ink,
    primaryContainer = Color(0xFF12383A),
    onPrimaryContainer = Color(0xFFBFF7EE),
    secondary = Color(0xFFB5A6FF),
    onSecondary = Color(0xFF1E1055),
    secondaryContainer = Color(0xFF2A2158),
    onSecondaryContainer = Color(0xFFE3DDFF),
    tertiary = Color(0xFF4FE0CF),
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF0F3A3A),
    onTertiaryContainer = Color(0xFFB9F5EC),
    error = Color(0xFFFF7A59),
    onError = Color(0xFF1A0A06),
    errorContainer = Color(0xFF5A2316),
    onErrorContainer = Color(0xFFFFD9CF),
    background = Color(0xFF0A0F20),
    onBackground = Color(0xFFEEF2FF),
    surface = Color(0xFF0A0F20),
    onSurface = Color(0xFFEEF2FF),
    surfaceVariant = Color(0xFF1A2440),
    onSurfaceVariant = Color(0xFFA9B4D0),
    surfaceTint = Color(0xFF5EEAD4),
    inverseSurface = Color(0xFFEEF2FF),
    inverseOnSurface = Ink,
    inversePrimary = Color(0xFF0F766E),
    outline = Color(0xFF6A769C),
    outlineVariant = Color(0xFF26304F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF212C50),
    surfaceDim = Color(0xFF080C1A),
    surfaceContainerLowest = Color(0xFF080C1A),
    surfaceContainerLow = Color(0xFF0F162C),
    surfaceContainer = Color(0xFF141C36),
    surfaceContainerHigh = Color(0xFF1A2442),
    surfaceContainerHighest = Color(0xFF212C50),
)

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF3EE),
    onPrimaryContainer = Color(0xFF053B36),
    secondary = Color(0xFF6D5BD6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E0FF),
    onSecondaryContainer = Color(0xFF2B1C7A),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5F2EE),
    onTertiaryContainer = Color(0xFF053B36),
    error = Color(0xFFC9361F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE0D9),
    onErrorContainer = Color(0xFF5C1408),
    background = Color(0xFFF4F6FD),
    onBackground = Color(0xFF10172E),
    surface = Color(0xFFF4F6FD),
    onSurface = Color(0xFF10172E),
    surfaceVariant = Color(0xFFE4E9F7),
    onSurfaceVariant = Color(0xFF4A5578),
    surfaceTint = Color(0xFF0F766E),
    inverseSurface = Color(0xFF10172E),
    inverseOnSurface = Color(0xFFF4F6FD),
    inversePrimary = Color(0xFF5EEAD4),
    outline = Color(0xFF7B86A8),
    outlineVariant = Color(0xFFD3DAEE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE2F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FF),
    surfaceContainer = Color(0xFFEEF1FB),
    surfaceContainerHigh = Color(0xFFE7EBF8),
    surfaceContainerHighest = Color(0xFFDFE4F4),
)

internal val DarkExtras = OrbitColors(
    isDark = true,
    bubbleTones = listOf(
        BubbleTone(Color(0xFF8CF3E3), Color(0xFF3FC7B8), Ink), // Aqua
        BubbleTone(Color(0xFFA6CCFF), Color(0xFF5B93F2), Ink), // Sky
        BubbleTone(Color(0xFFC9BBFF), Color(0xFF8F7BF2), Ink), // Violet
        BubbleTone(Color(0xFFFFB9D2), Color(0xFFF06A9B), Ink), // Rose
        BubbleTone(Color(0xFFFFDCA0), Color(0xFFF2A33C), Ink), // Amber
        BubbleTone(Color(0xFFE1E7F7), Color(0xFF9AA6C6), Ink), // Silver
    ),
    accent = Color(0xFF5EEAD4),
    accentStart = Color(0xFF5EEAD4),
    accentEnd = Color(0xFF4C8DFF),
    onAccent = Ink,
    softAccent = Color(0xFFB5A6FF),
    solidAccent = Color(0xFF4FE0CF),
    softFillStart = Color(0x2E8F7BF2),
    softFillEnd = Color(0x1A5EEAD4),
    solidFill = Color(0xFF0F2230),
    backgroundTop = Color(0xFF090E1F),
    backgroundBottom = Color(0xFF0E1730),
    heroGlow = Color(0xFF3FC7B8),
    blobs = emptyList(),
    glassFill = Color(0x12FFFFFF),
    glassFillStrong = Color(0x2BFFFFFF),
    glassBorderTop = Color(0x4DFFFFFF),
    glassBorderBottom = Color(0x0FFFFFFF),
    alert = Color(0xFFFF7A59),
    onAlert = Color(0xFF1A0A06),
    alertText = Color(0xFFFF7A59),
    scrim = Color(0xBD050813),
    ring = Color(0x2E5EEAD4),
    actionCall = ActionCall,
    actionWhatsApp = ActionWhatsApp,
    actionContact = ActionContact,
    actionDetails = ActionDetails,
    actionAi = ActionAi,
)

internal val LightExtras = OrbitColors(
    isDark = false,
    bubbleTones = listOf(
        BubbleTone(Color(0xFFD3F8F1), Color(0xFF9BE6DA), Color(0xFF0B4F49)), // Aqua
        BubbleTone(Color(0xFFDCEBFF), Color(0xFFAFCDFA), Color(0xFF153E82)), // Sky
        BubbleTone(Color(0xFFE6DEFF), Color(0xFFC6B7FA), Color(0xFF3B2A93)), // Violet
        BubbleTone(Color(0xFFFFE0EC), Color(0xFFFBB5CF), Color(0xFF8A1F4C)), // Rose
        BubbleTone(Color(0xFFFFEFD2), Color(0xFFFAD08B), Color(0xFF6B4300)), // Amber
        BubbleTone(Color(0xFFECEFF8), Color(0xFFCDD4E8), Color(0xFF2A3350)), // Silver
    ),
    accent = Color(0xFF0F766E),
    accentStart = Color(0xFF14B8A6),
    accentEnd = Color(0xFF3B6EF5),
    onAccent = Color(0xFFFFFFFF),
    softAccent = Color(0xFF6D5BD6),
    solidAccent = Color(0xFF0F766E),
    softFillStart = Color(0x246D5BD6),
    softFillEnd = Color(0x1F3B6EF5),
    solidFill = Color(0xFFE2F3F0),
    backgroundTop = Color(0xFFF7F8FF),
    backgroundBottom = Color(0xFFECF1FB),
    heroGlow = Color(0xFF9BE6DA),
    blobs = listOf(Color(0xFFE7E0FF), Color(0xFFFFE6D9), Color(0xFFD9F6EE)),
    glassFill = Color(0xA8FFFFFF),
    glassFillStrong = Color(0xD1FFFFFF),
    glassBorderTop = Color(0xF2FFFFFF),
    glassBorderBottom = Color(0x99C9D2EA),
    alert = Color(0xFFC9361F),
    onAlert = Color(0xFFFFFFFF),
    alertText = Color(0xFFC9361F),
    scrim = Color(0x9E1A1F3A),
    ring = Color(0x243B6EF5),
    actionCall = ActionCall,
    actionWhatsApp = ActionWhatsApp,
    actionContact = ActionContact,
    actionDetails = ActionDetails,
    actionAi = ActionAi,
)
