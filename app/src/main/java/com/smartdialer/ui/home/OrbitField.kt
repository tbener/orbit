package com.smartdialer.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.theme.Orbit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What a bubble gesture reports: the suggestion, where it sits on the orbit, the gesture and the bubble's window bounds. */
typealias OrbitGestureHandler = (IndexedValue<Suggestion>, OrbitSlot, BubbleGesture, Rect) -> Unit

/**
 * The home screen's field of people, in rows: the top bubble in the middle with a soft glow and #2 and #3
 * beside it, then a grid of four per row, held still. It scrolls vertically; more rows appear as
 * it nears the end. With system animations off, the glow does not breathe.
 */
@Composable
fun OrbitField(
    suggestions: List<Suggestion>,
    selectedId: String?,
    /** The bubble whose lifted copy is showing; the original is hidden but keeps its place. */
    hiddenId: String?,
    canLoadMore: Boolean,
    onGesture: OrbitGestureHandler,
    onLoadMore: () -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit,
) {
    val motion = Orbit.motion
    val colors = Orbit.colors
    val labelScale = min(LocalDensity.current.fontScale, 1.5f).coerceAtLeast(1f)
    val scroll = rememberScrollState()
    val seen = remember { mutableSetOf<String>() }

    val ambient = if (motion.reduced) null else rememberInfiniteTransition(label = "ambient")
    val breathe: State<Float>? = ambient?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(motion.breatheMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "breathe",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val layout = remember(suggestions.size, width, labelScale) { OrbitLayout.compute(suggestions.size, width, labelScale) }
        val contentHeight = layout.contentHeight.dp + bottomInset

        // Load the next ring when the end of the field is close to the bottom of the screen.
        val viewportPx = constraints.maxHeight
        val contentPx = with(LocalDensity.current) { contentHeight.toPx() }
        val nearEnd by remember(contentPx, viewportPx) { derivedStateOf { scroll.value + viewportPx >= contentPx - 240f } }
        LaunchedEffect(nearEnd, suggestions.size, canLoadMore) { if (nearEnd && canLoadMore) onLoadMore() }

        Box(
            Modifier
                .fillMaxSize()
                // Everything fades out softly under the search row instead of being cut off.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = 36.dp.toPx()),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(scroll),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
                    .drawBehind {
                        if (suggestions.isEmpty()) return@drawBehind
                        val center = Offset(size.width / 2f, OrbitLayout.HERO_CENTER_Y.dp.toPx())
                        val pulse = 0.88f + 0.12f * sin(2f * PI.toFloat() * (breathe?.value ?: 0f))
                        // A soft glow behind the top bubble.
                        val glowRadius = 250.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to colors.heroGlow.copy(alpha = (if (colors.isDark) 0.34f else 0.50f) * pulse),
                                0.55f to colors.heroGlow.copy(alpha = (if (colors.isDark) 0.10f else 0.18f) * pulse),
                                1f to Color.Transparent,
                                center = center,
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                            center = center,
                        )
                        // A very faint halo hugging the top bubble; no bubble sits on it.
                        layout.ringRadii.forEach { radius ->
                            drawCircle(
                                color = colors.ring.copy(alpha = colors.ring.alpha * 0.7f),
                                radius = radius.dp.toPx(),
                                center = center,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                    },
            ) {
                if (suggestions.isEmpty()) {
                    Box(Modifier.fillMaxWidth().offset(y = 64.dp), contentAlignment = Alignment.TopCenter) { empty() }
                }
                layout.slots.forEach { slot ->
                    val suggestion = suggestions[slot.index]
                    key(suggestion.contact.id) {
                        val labelWidth = maxOf(slot.diameter, slot.labelWidth)
                        val left by animateDpAsState((slot.center.x - labelWidth / 2f).dp, motion.move(380), label = "slotX")
                        val top by animateDpAsState((slot.center.y - slot.diameter / 2f).dp, motion.move(380), label = "slotY")
                        val firstTime = remember { seen.add(suggestion.contact.id) }
                        Box(
                            Modifier
                                .offset(left, top)
                                .width(labelWidth.dp)
                                .bubbleEntrance(play = firstTime, index = slot.index)
                                .alpha(if (suggestion.contact.id == hiddenId) 0f else 1f),
                        ) {
                            ContactBubble(
                                suggestion = suggestion,
                                rank = slot.index + 1,
                                ring = slot.ring,
                                diameter = slot.diameter.dp,
                                labelWidth = labelWidth.dp,
                                showSubtitle = slot.showSubtitle,
                                selected = suggestion.contact.id == selectedId,
                                onGesture = { gesture, bounds -> onGesture(IndexedValue(slot.index, suggestion), slot, gesture, bounds) },
                                modifier = Modifier.width(labelWidth.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
