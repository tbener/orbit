package com.smartdialer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.components.OrbitTextField
import com.smartdialer.ui.components.SendButton
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.glass
import com.smartdialer.ui.theme.glassCircle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** The bubble the quick actions belong to: who, where it sits on the orbit, and its bounds in the window. */
data class QuickTarget(
    val suggestion: Suggestion,
    val rank: Int,
    val slot: OrbitSlot,
    val anchor: Rect,
)

class QuickActionHandlers(
    val onCall: () -> Unit,
    val onWhatsApp: () -> Unit,
    val onContactCard: () -> Unit,
    val onDetails: () -> Unit,
    val onSaveInstruction: (String) -> Unit,
)

/** What happens when a quick-action button is chosen. */
sealed interface QuickActionKind {
    /** Runs [onClick] and closes the quick actions. */
    class Run(val onClick: () -> Unit) : QuickActionKind

    /** Collapses the arc and shows a floating field; [onSave] gets the text, then the quick actions close. */
    class Input(val onSave: (String) -> Unit) : QuickActionKind
}

/** One button on the arc. The arc lays out any number of these, so adding a button means adding a list entry. */
class QuickAction(
    val icon: ImageVector,
    /** Short, shown under the button. */
    val label: String,
    /** The full phrase a screen reader says. */
    val description: String,
    val kind: QuickActionKind,
)

/** The buttons shown around a bubble, in arc order. */
fun quickActionsFor(name: String, handlers: QuickActionHandlers): List<QuickAction> = listOf(
    QuickAction(Icons.Filled.Call, "Call", "Call $name", QuickActionKind.Run(handlers.onCall)),
    QuickAction(Icons.AutoMirrored.Filled.Chat, "WhatsApp", "Message $name on WhatsApp", QuickActionKind.Run(handlers.onWhatsApp)),
    QuickAction(Icons.Filled.Person, "Contact", "Open the contact card for $name", QuickActionKind.Run(handlers.onContactCard)),
    QuickAction(Icons.Filled.Info, "Details", "Open details for $name", QuickActionKind.Run(handlers.onDetails)),
    QuickAction(Icons.Filled.EditNote, "Note", "Add an instruction about $name", QuickActionKind.Input(handlers.onSaveInstruction)),
)

private const val LIFT_SCALE = 0.12f

/**
 * The quick actions: the tapped bubble lifts forward while the screen dims (and blurs on Android 12 and
 * up), the action buttons spring out of it along an arc, and a caption says why this person is suggested.
 * "Note" collapses the arc into a floating field. Dismissed by tapping outside, by back, or by choosing
 * an action.
 */
@Composable
fun QuickActionsOverlay(
    target: QuickTarget,
    actions: List<QuickAction>,
    onDismiss: () -> Unit,
) {
    val motion = Orbit.motion
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var inputMode by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    val buttonProgress = remember { actions.map { Animatable(0f) } }
    val lift = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val inputProgress = remember { Animatable(0f) }
    val captionProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { scrim.animateTo(1f, motion.enter()) }
        launch { lift.animateTo(1f, motion.lift()) }
        launch { captionProgress.animateTo(1f, motion.enter(delayMillis = motion.shortMillis)) }
        buttonProgress.forEachIndexed { i, progress ->
            launch {
                delay(i * motion.arcStaggerMillis.toLong())
                progress.animateTo(1f, motion.pop())
            }
        }
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            launch { captionProgress.animateTo(0f, motion.exit()) }
            launch { inputProgress.animateTo(0f, motion.exit()) }
            // The buttons fly back into the bubble, last one first.
            buttonProgress.asReversed().forEachIndexed { i, progress ->
                launch {
                    delay(i * (motion.arcStaggerMillis / 2).toLong())
                    progress.animateTo(0f, motion.exit(160))
                }
            }
            launch { lift.animateTo(0f, motion.exit(200)) }
            launch { scrim.animateTo(0f, motion.exit(220)) }
            if (!motion.reduced) delay(260)
            onDismiss()
        }
    }

    fun openInput() {
        inputMode = true
        scope.launch {
            launch { captionProgress.animateTo(0f, motion.exit()) }
            buttonProgress.forEach { progress -> launch { progress.animateTo(0f, motion.exit(160)) } }
            inputProgress.animateTo(1f, motion.pop())
        }
    }

    BackHandler { close() }

    val slot = target.slot
    val name = target.suggestion.contact.name
    val local = target.anchor.translate(-origin)
    val diameterPx = with(density) { slot.diameter.dp.toPx() }
    val circleCenter = Offset(local.center.x, local.top + diameterPx / 2f)
    val dismissLabel = "Close quick actions for $name"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .semantics { paneTitle = "Quick actions for $name" },
    ) {
        // Scrim: darkens the screen; tapping anywhere outside the buttons closes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim.value }
                .background(Orbit.colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = dismissLabel,
                    onClick = { close() },
                ),
        )
        // The lifted copy of the tapped bubble: bigger, brighter, above the dimmed screen.
        Box(
            modifier = Modifier
                .offset { IntOffset(local.left.roundToInt(), local.top.roundToInt()) }
                .width(with(density) { local.width.toDp() })
                .graphicsLayer {
                    val scale = 1f + LIFT_SCALE * lift.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, (diameterPx / 2f) / max(local.height, 1f))
                }
                .clearAndSetSemantics { },
        ) {
            val labelWidth = max(slot.diameter, slot.labelWidth)
            ContactBubble(
                suggestion = target.suggestion,
                rank = target.rank,
                ring = slot.ring,
                diameter = slot.diameter.dp,
                labelWidth = labelWidth.dp,
                showSubtitle = slot.showSubtitle,
                showMissed = true,
                selected = false,
                onGesture = null,
                lift = lift.value,
                modifier = Modifier.width(labelWidth.dp),
            )
        }

        // Labels show for every button, or (on large fonts, where they would crowd) only for the one being pressed.
        val showAllLabels = density.fontScale <= 1.15f
        var pressedIndex by remember { mutableStateOf(-1) }
        QuickArcLayout(
            anchor = local,
            circleCenter = circleCenter,
            bubbleRadiusPx = diameterPx / 2f * (1f + LIFT_SCALE),
            diameterPx = diameterPx,
            buttonProgress = { buttonProgress[it].value },
            showInput = inputMode,
            buttonCount = actions.size,
            modifier = Modifier.fillMaxSize(),
            caption = { CaptionPill(target.suggestion.reason, Modifier.graphicsLayer { alpha = captionProgress.value }) },
            button = { index ->
                val action = actions[index]
                ArcButton(
                    action = action,
                    progress = { buttonProgress[index].value },
                    onPressedChange = { pressed -> if (pressed) pressedIndex = index else if (pressedIndex == index) pressedIndex = -1 },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (val kind = action.kind) {
                            is QuickActionKind.Run -> { kind.onClick(); close() }
                            is QuickActionKind.Input -> openInput()
                        }
                    },
                )
            },
            label = { index ->
                if (showAllLabels || pressedIndex == index) {
                    ArcLabel(actions[index].label, progress = { buttonProgress[index].value })
                }
            },
            input = {
                val kind = actions.firstNotNullOfOrNull { it.kind as? QuickActionKind.Input }
                if (kind != null) {
                    FloatingInput(
                        name = name,
                        modifier = Modifier.graphicsLayer {
                            alpha = inputProgress.value.coerceIn(0f, 1f)
                            val scale = 0.9f + 0.1f * inputProgress.value.coerceIn(0f, 1f)
                            scaleX = scale
                            scaleY = scale
                        },
                        onSend = { text -> kind.onSave(text); close() },
                    )
                }
            },
        )
    }
}

/**
 * Places the caption, the buttons, their labels and the input using [QuickLayout]. Positions are computed
 * in the layout pass, and each button's progress (0 = inside the bubble, 1 = on the arc) is read in the
 * placement pass, so the spring animation never recomposes anything. A label sits on the outer side of its
 * button, away from the bubble.
 */
@Composable
private fun QuickArcLayout(
    anchor: Rect,
    circleCenter: Offset,
    bubbleRadiusPx: Float,
    diameterPx: Float,
    buttonProgress: (Int) -> Float,
    showInput: Boolean,
    buttonCount: Int,
    modifier: Modifier,
    caption: @Composable () -> Unit,
    button: @Composable (Int) -> Unit,
    label: @Composable (Int) -> Unit,
    input: @Composable () -> Unit,
) {
    val insets = WindowInsets.safeDrawing
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId("caption")) { caption() }
            repeat(buttonCount) { i -> Box(Modifier.layoutId(i)) { button(i) } }
            repeat(buttonCount) { i -> Box(Modifier.layoutId("label$i")) { label(i) } }
            if (showInput) Box(Modifier.layoutId("input")) { input() }
        },
    ) { measurables, constraints ->
        val margin = Orbit.spacing.md.roundToPx()
        val bounds = Rect(
            insets.getLeft(this, layoutDirection) + margin.toFloat(),
            insets.getTop(this) + margin.toFloat(),
            constraints.maxWidth - insets.getRight(this, layoutDirection) - margin.toFloat(),
            constraints.maxHeight - insets.getBottom(this) - margin.toFloat(),
        )
        val maxCaption = min(Orbit.sizing.arcButton.roundToPx() * 6, bounds.width.roundToInt().coerceAtLeast(0))
        val captionP = measurables.first { it.layoutId == "caption" }.measure(Constraints(maxWidth = maxCaption))
        val buttonPs = (0 until buttonCount).map { i -> measurables.first { it.layoutId == i }.measure(Constraints()) }
        val footprintWidth = Orbit.sizing.arcButton.toPx() + Orbit.spacing.sm.toPx()
        val labelPs = (0 until buttonCount).map { i ->
            measurables.first { it.layoutId == "label$i" }.measure(Constraints(maxWidth = footprintWidth.roundToInt()))
        }
        val inputP = measurables.firstOrNull { it.layoutId == "input" }
            ?.measure(Constraints(maxWidth = min(Orbit.sizing.arcButton.roundToPx() * 7, bounds.width.roundToInt().coerceAtLeast(0))))

        val buttonSize = Orbit.sizing.arcButton.toPx()
        // The room reserved for a label is fixed, so the arc does not change shape when labels show or hide.
        val labelHeight = (Orbit.spacing.xl - Orbit.spacing.xs).toPx()
        val nameRect = Rect(anchor.left, anchor.top + diameterPx, anchor.right, anchor.bottom + diameterPx * LIFT_SCALE / 2f)
        val result = QuickLayout.place(
            bubbleCenter = circleCenter,
            bubbleRadius = bubbleRadiusPx,
            buttonCount = buttonCount,
            buttonSize = buttonSize,
            labelHeight = labelHeight,
            nameRect = nameRect,
            captionSize = Size(captionP.width.toFloat(), captionP.height.toFloat()),
            bounds = bounds,
            gap = Orbit.spacing.sm.toPx(),
        )
        val inputAt = inputP?.let {
            QuickLayout.placeBelowOrAbove(
                anchor = Rect(anchor.left, anchor.top, anchor.right, nameRect.bottom),
                size = Size(it.width.toFloat(), it.height.toFloat()),
                bounds = bounds,
                gap = Orbit.spacing.md.toPx(),
            )
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            captionP.place(result.captionTopLeft.x.roundToInt(), result.captionTopLeft.y.roundToInt())
            buttonPs.forEachIndexed { i, placeable ->
                val target = result.buttonCenters[i]
                val p = buttonProgress(i)
                val at = Offset(
                    circleCenter.x + (target.x - circleCenter.x) * p,
                    circleCenter.y + (target.y - circleCenter.y) * p,
                )
                placeable.place((at.x - placeable.width / 2f).roundToInt(), (at.y - buttonSize / 2f).roundToInt())
                val labelPlaceable = labelPs[i]
                val labelAbove = target.y < circleCenter.y - 1f
                val labelTop = if (labelAbove) at.y - buttonSize / 2f - labelPlaceable.height else at.y + buttonSize / 2f
                labelPlaceable.place((at.x - labelPlaceable.width / 2f).roundToInt(), labelTop.roundToInt())
            }
            if (inputP != null && inputAt != null) inputP.place(inputAt.x.roundToInt(), inputAt.y.roundToInt())
        }
    }
}

private fun min(a: Int, b: Int) = if (a < b) a else b

/** A floating frosted-glass circle with an icon. Reports when it is pressed so the label can show. */
@Composable
private fun ArcButton(
    action: QuickAction,
    progress: () -> Float,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressedChange(pressed) }
    Box(
        modifier = Modifier
            .size(Orbit.sizing.arcButton)
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = p
                val scale = (0.35f + 0.65f * p) * (if (pressed) 0.94f else 1f)
                scaleX = scale
                scaleY = scale
            }
            .glassCircle(strong = true, elevation = Orbit.elevation.medium)
            .clearAndSetSemantics {
                contentDescription = action.description
                role = Role.Button
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            action.icon,
            contentDescription = null,
            tint = if (pressed) Orbit.colors.accent else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(Orbit.sizing.icon),
        )
    }
}

/** The short label beside an arc button. It fades in with the button. */
@Composable
private fun ArcLabel(text: String, progress: () -> Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer { alpha = progress().coerceIn(0f, 1f) },
    )
}

/** The "why this contact" note: a glass pill, visually separate from the round buttons. */
@Composable
private fun CaptionPill(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            // An opaque base under the glass, so the dimmed bubbles behind never show through the text.
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .glass(MaterialTheme.shapes.large, strong = true, elevation = Orbit.elevation.medium)
            .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Orbit.spacing.sm)
                .background(Orbit.colors.accent, CircleShape),
        )
        Box(Modifier.width(Orbit.spacing.md))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The floating glass field that replaces the arc for "Note": type, then send. */
@Composable
private fun FloatingInput(name: String, modifier: Modifier, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focus.requestFocus()
    }
    OrbitTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = "Instruction about $name",
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.widthIn(min = Orbit.sizing.avatarLarge * 2).focusRequester(focus),
        trailing = { SendButton(enabled = text.isNotBlank(), description = "Save instruction about $name", onClick = { onSend(text.trim()) }) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text.trim()) }),
    )
}
