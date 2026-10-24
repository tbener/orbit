package com.smartdialer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** The spacing scale. Use these instead of raw dp values. */
@Immutable
class Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Elevation (shadow) levels. Priority is shown in part by depth and glow: bigger bubbles sit higher. */
@Immutable
class Elevation {
    val none = 0.dp
    val low = 2.dp
    val medium = 6.dp
    val high = 14.dp
    val popup = 20.dp
}

/** Fixed sizes shared by several components. */
@Immutable
class Sizing {
    /** The smallest tappable area, per Android accessibility guidance. */
    val touchTarget = 48.dp
    val iconSmall = 18.dp
    val icon = 24.dp
    val avatarMedium = 48.dp
    val avatarLarge = 96.dp
    val emptyStateIcon = 36.dp

    /** The hairline around glass surfaces and orbs. */
    val hairline = 1.dp

    /** The floating round buttons of the quick-actions arc. */
    val arcButton = 58.dp
}

/** Shape scale. Glass cards use 24, soft rows 26, solid rows 8, tags 10. */
internal val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fully rounded ends, for the search field, chips and the dialer button. */
val PillShape = RoundedCornerShape(percent = 50)

/** Crisp, nearly square corners: the look of typed rules, which are enforced by code. */
val CrispShape = RoundedCornerShape(8.dp)

/** Soft, generous corners: the look of free-text rules, which the LLM interprets. */
val SoftShape = RoundedCornerShape(26.dp)
