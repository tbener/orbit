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

/** Elevation (shadow) levels. Priority is shown in part by depth: bigger bubbles sit higher. */
@Immutable
class Elevation {
    val none = 0.dp
    val low = 1.dp
    val medium = 3.dp
    val high = 6.dp
    val popup = 10.dp
}

/** Fixed sizes shared by several components. */
@Immutable
class Sizing {
    /** The smallest tappable area, per Android accessibility guidance. */
    val touchTarget = 48.dp
    val iconSmall = 18.dp
    val icon = 24.dp
    val avatarMedium = 48.dp
    val avatarLarge = 88.dp
    val popupWidth = 304.dp
    val emptyStateIcon = 40.dp
}

/** Shape scale: small for tags, medium for inputs and rows, large for cards and the popup. */
internal val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fully rounded ends, for search fields, chips and the main button. */
val PillShape = RoundedCornerShape(percent = 50)
