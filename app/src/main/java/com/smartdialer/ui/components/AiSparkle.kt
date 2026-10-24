package com.smartdialer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartdialer.ui.theme.Orbit
import kotlin.math.PI
import kotlin.math.sin

/** Orbit's own icons. */
object OrbitIcons {
    /**
     * The AI sparkle: a large four-point star and a small one. Defined once and reused by the "Note" action, the
     * header's "add general instruction" button and the AI status indicator.
     */
    val Sparkle: ImageVector by lazy {
        ImageVector.Builder("Sparkle", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 2.5f)
                quadTo(11f, 10f, 18.5f, 11f)
                quadTo(11f, 12f, 10f, 19.5f)
                quadTo(9f, 12f, 1.5f, 11f)
                quadTo(9f, 10f, 10f, 2.5f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 14f)
                quadTo(19.4f, 17.6f, 23f, 18f)
                quadTo(19.4f, 18.4f, 19f, 22f)
                quadTo(18.6f, 18.4f, 15f, 18f)
                quadTo(18.6f, 17.6f, 19f, 14f)
                close()
            }
        }.build()
    }
}

/** What the AI indicator shows. */
enum class AiIndicatorState {
    /** A real refresh is running: the sparkle shimmers (still when animations are off). */
    Working,

    /** AI is paused or unavailable: a dimmed sparkle with a small slash. */
    Paused,
}

/** The small AI sparkle next to the wordmark. Hidden entirely when nothing needs saying. */
@Composable
fun AiIndicator(state: AiIndicatorState, modifier: Modifier = Modifier) {
    val motion = Orbit.motion
    val accent = Orbit.colors.actionAi
    val pulse = if (state == AiIndicatorState.Working && !motion.reduced) {
        val t by rememberInfiniteTransition(label = "sparkle").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "t",
        )
        0.5f + 0.5f * sin(2f * PI.toFloat() * t)
    } else {
        1f
    }
    Box(modifier.size(24.dp)) {
        Icon(
            imageVector = OrbitIcons.Sparkle,
            contentDescription = null,
            tint = if (state == AiIndicatorState.Working) accent.top else Orbit.colors.ring.copy(alpha = 1f),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    val scale = if (state == AiIndicatorState.Working) 0.85f + 0.25f * pulse else 1f
                    scaleX = scale
                    scaleY = scale
                    alpha = if (state == AiIndicatorState.Working) 0.55f + 0.45f * pulse else 0.55f
                }
                .drawWithContent {
                    drawContent()
                    if (state == AiIndicatorState.Paused) {
                        // A small slash through the sparkle.
                        drawLine(
                            color = Color(0xFFA9B4D0),
                            start = Offset(size.width * 0.12f, size.height * 0.88f),
                            end = Offset(size.width * 0.88f, size.height * 0.12f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                },
        )
    }
}

@Suppress("unused")
private val IndicatorSize: Dp = 24.dp
