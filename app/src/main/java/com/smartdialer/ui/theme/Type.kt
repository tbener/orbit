@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.smartdialer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.smartdialer.R

/**
 * Outfit (SIL Open Font License, see docs/licenses): a geometric, slightly rounded sans that is friendly
 * without being childish. One variable font file provides every weight.
 */
val OrbitFont = FontFamily(
    Font(R.font.outfit, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.outfit, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.outfit, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.outfit, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.outfit, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = OrbitFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
)

/**
 * The type scale. Sizes are in sp so they grow with the user's font setting.
 *
 * - headlineSmall: a contact's name on Details
 * - titleLarge: screen titles ("Who to call", "Rules")
 * - titleMedium: section headers, the top bubble's name
 * - titleSmall: card titles, dialog headings
 * - bodyLarge / bodyMedium: lists and notes (the "why" text, rule text)
 * - bodySmall: one-line subtitles and helper text
 * - labelLarge: bubble names, buttons
 * - labelMedium / labelSmall: bubble subtitles, tags, quick-action labels
 */
internal val OrbitTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Light),
    displayMedium = style(45, 52, FontWeight.Light),
    displaySmall = style(36, 44, FontWeight.Normal),
    headlineLarge = style(32, 40, FontWeight.Medium),
    headlineMedium = style(28, 36, FontWeight.Medium),
    headlineSmall = style(28, 34, FontWeight.Medium, -0.2),
    titleLarge = style(28, 34, FontWeight.Medium, -0.3),
    titleMedium = style(17, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(15, 21, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.15),
    bodySmall = style(13, 18, FontWeight.Normal, 0.2),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.3),
    labelSmall = style(12, 16, FontWeight.Medium, 0.3),
)
