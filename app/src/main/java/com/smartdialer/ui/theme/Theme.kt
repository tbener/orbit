package com.smartdialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E4E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FA),
    onPrimaryContainer = Color(0xFF0F2F57),
    tertiary = Color(0xFF0F6B63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEBE7),
    onTertiaryContainer = Color(0xFF00302C),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEFEDEA),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C8F2),
    onPrimary = Color(0xFF0B2C54),
    primaryContainer = Color(0xFF1F4170),
    onPrimaryContainer = Color(0xFFD3E4FA),
    tertiary = Color(0xFF8FD6CD),
    onTertiary = Color(0xFF00332E),
    tertiaryContainer = Color(0xFF0B4F49),
    onTertiaryContainer = Color(0xFFB2F0E8),
    background = Color(0xFF121315),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121315),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2A2B2E),
    onSurfaceVariant = Color(0xFFC9C5CC),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun OrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
