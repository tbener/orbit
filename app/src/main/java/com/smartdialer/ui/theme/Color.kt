package com.smartdialer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One tone of the bubble palette: a soft and a strong fill, and the initials color that is legible on both. */
@Immutable
class BubbleTone(val soft: Color, val strong: Color, val content: Color)

/** Colors the Material 3 scheme has no role for. Read them with `Orbit.colors`. */
@Immutable
class OrbitColors(
    /** The people palette. A contact keeps the same tone for good; priority is shown by size, not color. */
    val bubbleTones: List<BubbleTone>,
    /** Alert-colored text on the background (for example "Missed"). Stronger than `error` for small text. */
    val alertText: Color,
    /** Dims the screen behind the quick-actions card. */
    val scrim: Color,
    /** The faint rings drawn behind the top bubble. */
    val orbitRing: Color,
)

// Warm paper and ink, an indigo accent for "Orbit", a teal for hard (enforced) rules, a coral for alerts.
// Every text pair below was checked for at least 4.5:1 contrast (see docs/DESIGN.md).

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF4650C8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E4FB),
    onPrimaryContainer = Color(0xFF1D2570),
    secondary = Color(0xFF6B5E52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEE3D8),
    onSecondaryContainer = Color(0xFF2A2118),
    tertiary = Color(0xFF1F7A6D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3EDE7),
    onTertiaryContainer = Color(0xFF0B3A33),
    error = Color(0xFFBA3A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDADA),
    onErrorContainer = Color(0xFF5C1111),
    background = Color(0xFFFBF8F4),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFBF8F4),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEFE9E1),
    onSurfaceVariant = Color(0xFF52473E),
    surfaceTint = Color(0xFF4650C8),
    inverseSurface = Color(0xFF33302B),
    inverseOnSurface = Color(0xFFF6F0E8),
    inversePrimary = Color(0xFFBFC5FF),
    outline = Color(0xFF857A6E),
    outlineVariant = Color(0xFFD9D0C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8F4),
    surfaceDim = Color(0xFFE2DBD2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1EB),
    surfaceContainer = Color(0xFFF1EBE4),
    surfaceContainerHigh = Color(0xFFECE5DD),
    surfaceContainerHighest = Color(0xFFE6DFD6),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFBFC5FF),
    onPrimary = Color(0xFF1B2270),
    primaryContainer = Color(0xFF32399A),
    onPrimaryContainer = Color(0xFFE0E2FF),
    secondary = Color(0xFFD6C8BB),
    onSecondary = Color(0xFF3A3027),
    secondaryContainer = Color(0xFF52473E),
    onSecondaryContainer = Color(0xFFF2E4D7),
    tertiary = Color(0xFF8FD6CB),
    onTertiary = Color(0xFF00332D),
    tertiaryContainer = Color(0xFF0F4F46),
    onTertiaryContainer = Color(0xFFB6F0E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF14120F),
    onBackground = Color(0xFFEAE2D8),
    surface = Color(0xFF14120F),
    onSurface = Color(0xFFEAE2D8),
    surfaceVariant = Color(0xFF3A352F),
    onSurfaceVariant = Color(0xFFCFC6BA),
    surfaceTint = Color(0xFFBFC5FF),
    inverseSurface = Color(0xFFEAE2D8),
    inverseOnSurface = Color(0xFF33302B),
    inversePrimary = Color(0xFF4650C8),
    outline = Color(0xFF988F83),
    outlineVariant = Color(0xFF4A443D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3731),
    surfaceDim = Color(0xFF14120F),
    surfaceContainerLowest = Color(0xFF0F0D0B),
    surfaceContainerLow = Color(0xFF1C1915),
    surfaceContainer = Color(0xFF211E1A),
    surfaceContainerHigh = Color(0xFF2B2722),
    surfaceContainerHighest = Color(0xFF36312B),
)

internal val LightExtras = OrbitColors(
    bubbleTones = listOf(
        BubbleTone(Color(0xFFE3E6FB), Color(0xFFCDD2F7), Color(0xFF2B3699)), // Indigo
        BubbleTone(Color(0xFFF8E0E3), Color(0xFFF2C9CF), Color(0xFF8A2433)), // Rose
        BubbleTone(Color(0xFFDFEDE0), Color(0xFFC8E0CB), Color(0xFF1F5F2B)), // Sage
        BubbleTone(Color(0xFFF8E8C8), Color(0xFFF1DAA6), Color(0xFF6E4B00)), // Amber
        BubbleTone(Color(0xFFDCEBF6), Color(0xFFC4DFF1), Color(0xFF1B5A80)), // Sky
        BubbleTone(Color(0xFFEBE2F6), Color(0xFFDBCDEF), Color(0xFF5B3A8C)), // Lilac
        BubbleTone(Color(0xFFF0EBE4), Color(0xFFE4DDD3), Color(0xFF3F372F)), // Stone
    ),
    alertText = Color(0xFF8F2A2A),
    scrim = Color(0x52000000),
    orbitRing = Color(0x1F4650C8),
)

internal val DarkExtras = OrbitColors(
    bubbleTones = listOf(
        BubbleTone(Color(0xFF2A3066), Color(0xFF38418A), Color(0xFFD9DDFF)), // Indigo
        BubbleTone(Color(0xFF4A2229), Color(0xFF66303A), Color(0xFFFFD9DE)), // Rose
        BubbleTone(Color(0xFF1F3A25), Color(0xFF2B5233), Color(0xFFD2EED5)), // Sage
        BubbleTone(Color(0xFF46350F), Color(0xFF5E4818), Color(0xFFFFE6B3)), // Amber
        BubbleTone(Color(0xFF1B3A4D), Color(0xFF265068), Color(0xFFCDE8F8)), // Sky
        BubbleTone(Color(0xFF36274F), Color(0xFF4A3669), Color(0xFFEADDFF)), // Lilac
        BubbleTone(Color(0xFF34302B), Color(0xFF433E37), Color(0xFFEDE6DD)), // Stone
    ),
    alertText = Color(0xFFFFB4AB),
    scrim = Color(0x80000000),
    orbitRing = Color(0x33BFC5FF),
)
