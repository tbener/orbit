package com.smartdialer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A frosted-glass surface: a translucent fill, a thin border that is brighter at the top, and a soft
 * shadow. The shapes behind it are blurred by the screen, not by this modifier.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    strong: Boolean = false,
    elevation: Dp = 0.dp,
): Modifier {
    val colors = Orbit.colors
    return this
        .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
        .background(if (strong) colors.glassFillStrong else colors.glassFill, shape)
        .border(
            width = Orbit.sizing.hairline,
            brush = Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)),
            shape = shape,
        )
}

/** A frosted-glass circle, used for icon buttons. */
@Composable
fun Modifier.glassCircle(strong: Boolean = false, elevation: Dp = 0.dp): Modifier = glass(CircleShape, strong, elevation)

/**
 * The backdrop of every screen. Dark: deep ink with a faint scatter of stars. Light: soft off-white with
 * a few pastel blobs of light. Content scrolls over it.
 */
@Composable
fun OrbitBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Orbit.colors
    val stars = remember { Stars }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
                if (colors.isDark) {
                    stars.forEach { (x, y, r, a) ->
                        drawCircle(Color.White.copy(alpha = a), radius = r * density, center = Offset(x * size.width, y * size.height))
                    }
                } else {
                    val spots = listOf(Offset(0.05f, 0.08f), Offset(0.95f, 0.38f), Offset(0.2f, 0.92f))
                    colors.blobs.forEachIndexed { i, blob ->
                        val c = spots[i % spots.size]
                        val center = Offset(c.x * size.width, c.y * size.height)
                        val radius = size.width * 0.75f
                        drawCircle(
                            brush = Brush.radialGradient(listOf(blob.copy(alpha = 0.85f), Color.Transparent), center, radius),
                            radius = radius,
                            center = center,
                        )
                    }
                }
            },
    ) {
        content()
    }
}

/** A fixed, deterministic scatter of faint stars: x and y as fractions of the screen, radius in dp, alpha. */
private val Stars: List<List<Float>> = Random(7).let { random ->
    List(46) {
        listOf(random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat() * 0.9f, 0.10f + random.nextFloat() * 0.28f)
    }
}

