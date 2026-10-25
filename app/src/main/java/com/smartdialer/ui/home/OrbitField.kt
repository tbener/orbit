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
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.smartdialer.ranking.Reorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.smartdialer.data.LayoutMode
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
    mode: LayoutMode,
    /** Drag is off while the search or a quick-actions arc is open. */
    dragEnabled: Boolean,
    /** Contact id to the slot it is pinned to. */
    pinnedSlots: Map<String, Int>,
    /** A drag started or ended (new results are held back meanwhile). */
    onDragActive: (Boolean) -> Unit,
    /** A bubble was dropped: from and to are 1-based slots. */
    onDrop: (from: Int, to: Int) -> Unit,
    /** TalkBack's "Move to position...": the bubble and its slot. */
    onMoveRequest: (Suggestion, Int) -> Unit,
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
        val layout = remember(suggestions.size, width, labelScale, mode) { OrbitLayout.compute(suggestions.size, width, labelScale, mode) }
        val contentHeight = layout.contentHeight.dp + bottomInset

        // ---- Drag to reorder. Slots are rank positions; the other bubbles slide aside using the normal slot animation.
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val density = LocalDensity.current
        val ids = suggestions.map { it.contact.id }
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragDelta by remember { mutableStateOf(Offset.Zero) }
        var pressedId by remember { mutableStateOf<String?>(null) }
        var target by remember { mutableIntStateOf(-1) }
        val pinnedSlotSet = pinnedSlots.values.toSet()
        fun blocked(index: Int) = index >= 0 && index != dragFrom && (index + 1) in pinnedSlotSet

        val preview: List<String> = if (dragId != null && target >= 0 && target != dragFrom && !blocked(target)) {
            when (val r = Reorder.move(ids, pinnedSlots, dragFrom + 1, target + 1)) {
                is Reorder.Result.Moved -> r.order
                is Reorder.Result.PinMoved -> r.order
                else -> ids
            }
        } else {
            ids
        }
        val byId = suggestions.associateBy { it.contact.id }
        val ordered = preview.mapNotNull(byId::get)

        /** The dragged bubble's center in the field, in dp, including how far the field scrolled since the drag began. */
        fun dragCenter(): Offset {
            val origin = layout.slots[dragFrom].center
            val d = density.density
            return Offset(origin.x + dragDelta.x / d, origin.y + dragDelta.y / d)
        }

        fun updateTarget() {
            if (dragId == null || dragFrom !in layout.slots.indices) return
            val c = dragCenter()
            val nearest = layout.slots.minBy { (it.center - c).getDistance() }.index
            if (nearest != target) {
                target = nearest
                if (blocked(nearest)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        fun startDrag(index: Int) {
            dragId = ids[index]
            dragFrom = index
            dragDelta = Offset.Zero
            target = index
            onDragActive(true)
        }

        fun finishDrag(moved: Boolean) {
            val id = dragId ?: return
            val from = dragFrom
            val to = target
            scope.launch {
                val startDelta = dragDelta
                val settle: Offset? = when {
                    !moved || to < 0 || to == from -> null
                    blocked(to) -> Offset.Zero // back to where it came from
                    else -> {
                        val d = density.density
                        val c = layout.slots[to].center
                        val o = layout.slots[from].center
                        Offset((c.x - o.x) * d, (c.y - o.y) * d)
                    }
                }
                if (settle != null && !motion.reduced) {
                    animate(0f, 1f, animationSpec = tween(160)) { v, _ -> dragDelta = startDelta + (settle - startDelta) * v }
                }
                if (moved && to >= 0 && to != from && !blocked(to)) onDrop(from + 1, to + 1)
                dragId = null
                target = -1
                dragDelta = Offset.Zero
                onDragActive(false)
                @Suppress("UNUSED_EXPRESSION") id
            }
        }

        // Near the top or bottom edge the field scrolls while dragging.
        LaunchedEffect(dragId) {
            while (dragId != null) {
                val d = density.density
                // The finger's place on screen: where the bubble is in the field, minus how far the field has scrolled.
                val y = layout.slots[dragFrom].center.y * d + dragDelta.y - scroll.value
                val edge = with(density) { 96.dp.toPx() }
                val bottomLimit = constraints.maxHeight - with(density) { bottomInset.toPx() } - edge / 2f
                val scrolled = when {
                    y < edge -> scroll.scrollBy(-20f)
                    y > bottomLimit -> scroll.scrollBy(20f)
                    else -> 0f
                }
                // A still finger sees the field move under it: keep the bubble with it until the next touch event.
                dragDelta += Offset(0f, scrolled)
                updateTarget()
                delay(16)
            }
        }

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
                        val center = Offset(layout.center.x.dp.toPx(), layout.center.y.dp.toPx())
                        val pulse = 0.88f + 0.12f * sin(2f * PI.toFloat() * (breathe?.value ?: 0f))
                        // A soft glow behind the top bubble.
                        val glowRadius = (if (mode == LayoutMode.Spotlight) 250 else 170).dp.toPx()
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
                // Where the dragged bubble would land: a ring with the slot number, or a "not allowed" cue on a pinned slot.
                if (dragId != null && target >= 0 && target != dragFrom && target in layout.slots.indices) {
                    val s = layout.slots[target]
                    if (blocked(target)) {
                        Box(
                            Modifier
                                .offset((s.center.x - 16f).dp, (s.center.y - 16f).dp)
                                .size(32.dp)
                                .background(colors.alert, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = colors.onAlert, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Box(
                            Modifier
                                .offset((s.center.x - s.diameter / 2f).dp, (s.center.y - s.diameter / 2f).dp)
                                .size(s.diameter.dp)
                                .background(colors.accent.copy(alpha = 0.12f), CircleShape)
                                .border(2.dp, colors.accent.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${target + 1}", style = MaterialTheme.typography.titleMedium, color = colors.accent)
                        }
                    }
                }
                // Gestures live on still overlays over each bubble's original slot, so positions stay stable while one is dragged.
                suggestions.forEachIndexed { original, suggestion ->
                    key("gesture-${suggestion.contact.id}") {
                        val s = layout.slots[original]
                        val w = maxOf(s.diameter, s.labelWidth)
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        Box(
                            Modifier
                                .offset((s.center.x - w / 2f).dp, (s.center.y - s.diameter / 2f).dp)
                                .size(w.dp, (s.diameter + s.labelHeight).dp)
                                .zIndex(20f)
                                .onGloballyPositioned { bounds = it.boundsInWindow() }
                                .bubbleGestures(
                                    onPressedChange = { pressedId = if (it) suggestion.contact.id else null },
                                    onTap = { onGesture(IndexedValue(original, suggestion), s, BubbleGesture.Tap, bounds) },
                                    onHoldRelease = { onGesture(IndexedValue(original, suggestion), s, BubbleGesture.LongPress, bounds) },
                                    drag = {
                                        BubbleDragCallbacks(
                                            enabled = { dragEnabled },
                                            onStart = { startDrag(original) },
                                            onMove = { offset -> dragDelta = offset; updateTarget() },
                                            onEnd = ::finishDrag,
                                        )
                                    },
                                ),
                        )
                    }
                }
                ordered.forEachIndexed { index, suggestion ->
                    val slot = layout.slots[index]
                    key(suggestion.contact.id) {
                        val id = suggestion.contact.id
                        val original = ids.indexOf(id)
                        val originalSlot = layout.slots[original]
                        val isDragged = id == dragId
                        val labelWidth = maxOf(slot.diameter, slot.labelWidth)
                        val left by animateDpAsState((slot.center.x - labelWidth / 2f).dp, motion.move(380), label = "slotX")
                        val top by animateDpAsState((slot.center.y - slot.diameter / 2f).dp, motion.move(380), label = "slotY")
                        val firstTime = remember { seen.add(id) }
                        val lift by animateFloatAsState(if (isDragged) 1f else 0f, if (motion.reduced) snap() else tween(140), label = "lift")
                        Box(
                            Modifier
                                .then(
                                    if (isDragged) {
                                        // Follows the finger, at the size of the slot it would land in.
                                        Modifier.offset {
                                            val c = dragCenter()
                                            IntOffset(((c.x - labelWidth / 2f) * density.density).roundToInt(), ((c.y - slot.diameter / 2f) * density.density).roundToInt())
                                        }
                                    } else {
                                        Modifier.offset(left, top)
                                    },
                                )
                                .zIndex(if (isDragged) 10f else 0f)
                                .graphicsLayer {
                                    val scale = 1f + 0.12f * lift
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .width(labelWidth.dp)
                                .bubbleEntrance(play = firstTime, index = slot.index)
                                .alpha(if (id == hiddenId) 0f else 1f),
                        ) {
                            ContactBubble(
                                suggestion = suggestion,
                                rank = slot.index + 1,
                                ring = slot.ring,
                                diameter = slot.diameter.dp,
                                labelWidth = labelWidth.dp,
                                showSubtitle = slot.showSubtitle,
                                selected = id == selectedId && dragId == null,
                                lift = lift,
                                onGesture = { gesture, bounds ->
                                    onGesture(IndexedValue(original, suggestion), originalSlot, gesture, bounds)
                                },
                                pressed = pressedId == id,
                                onMoveRequest = { onMoveRequest(suggestion, original + 1) },
                                modifier = Modifier.width(labelWidth.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
