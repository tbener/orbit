package com.smartdialer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.theme.Orbit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The bubble the quick-actions card belongs to, with its bounds in the window. */
data class QuickTarget(
    val suggestion: Suggestion,
    val rank: Int,
    val tier: BubbleTier,
    val anchor: Rect,
)

class QuickActionHandlers(
    val onCall: () -> Unit,
    val onWhatsApp: () -> Unit,
    val onContactCard: () -> Unit,
    val onDetails: () -> Unit,
    val onSaveInstruction: (String) -> Unit,
)

private class QuickButton(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Dims the screen, keeps the tapped bubble visible, and shows the quick-actions card next to it.
 * Dismissed by tapping outside, by back, or by choosing an action.
 */
@Composable
fun QuickActionsOverlay(
    target: QuickTarget,
    handlers: QuickActionHandlers,
    onDismiss: () -> Unit,
) {
    val motion = Orbit.motion
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) motion.enter() else motion.exit(),
        label = "quickActions",
    )

    fun close() {
        scope.launch {
            shown = false
            if (!motion.reduced) delay(motion.shortMillis.toLong())
            onDismiss()
        }
    }

    fun act(action: () -> Unit) {
        action()
        close()
    }

    BackHandler { close() }

    val local = target.anchor.translate(-origin)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        // Scrim: tapping anywhere outside the card dismisses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(Orbit.colors.scrim)
                .pointerInput(Unit) { detectTapGestures { close() } },
        )
        // A display-only copy of the tapped bubble, so it stays undimmed.
        Box(
            modifier = Modifier
                .offset { IntOffset(local.left.roundToInt(), local.top.roundToInt()) }
                .width(with(density) { local.width.toDp() })
                .clearAndSetSemantics { },
        ) {
            ContactBubble(
                suggestion = target.suggestion,
                rank = target.rank,
                tier = target.tier,
                selected = true,
                onGesture = null,
            )
        }
        AnchoredLayout(
            anchor = IntRect(local.left.roundToInt(), local.top.roundToInt(), local.right.roundToInt(), local.bottom.roundToInt()),
            modifier = Modifier.fillMaxSize(),
        ) {
            QuickActionsCard(
                name = target.suggestion.contact.name,
                reason = target.suggestion.reason,
                handlers = handlers,
                onAct = ::act,
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    val scale = 0.92f + 0.08f * progress
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}

/** Places its single child next to [anchor] using [PopupPlacement], inside the safe area and above the keyboard. */
@Composable
private fun AnchoredLayout(anchor: IntRect, modifier: Modifier, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    val margin = Orbit.spacing.md
    val gap = Orbit.spacing.md
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val marginPx = margin.roundToPx()
        val bounds = IntRect(
            left = insets.getLeft(this, layoutDirection) + marginPx,
            top = insets.getTop(this) + marginPx,
            right = constraints.maxWidth - insets.getRight(this, layoutDirection) - marginPx,
            bottom = constraints.maxHeight - insets.getBottom(this) - marginPx,
        )
        val card = measurables.first().measure(
            Constraints(maxWidth = bounds.width.coerceAtLeast(0), maxHeight = bounds.height.coerceAtLeast(0)),
        )
        val offset = PopupPlacement.place(anchor, IntSize(card.width, card.height), bounds, gap = gap.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(offset) }
    }
}

@Composable
internal fun QuickActionsCard(
    name: String,
    reason: String,
    handlers: QuickActionHandlers,
    onAct: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(adding) { if (adding) focus.requestFocus() }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Orbit.elevation.medium,
        shadowElevation = Orbit.elevation.popup,
        modifier = modifier
            .width(Orbit.sizing.popupWidth)
            // Taps inside the card must not fall through to the dismissing scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Quick actions for $name" },
    ) {
        Column(Modifier.padding(Orbit.spacing.lg)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Orbit.spacing.xs),
            )
            Spacer(Modifier.height(Orbit.spacing.md))
            val buttons = listOf(
                QuickButton(Icons.Filled.Call, "Call") { onAct(handlers.onCall) },
                QuickButton(Icons.AutoMirrored.Filled.Chat, "WhatsApp") { onAct(handlers.onWhatsApp) },
                QuickButton(Icons.Filled.Person, "Contact card") { onAct(handlers.onContactCard) },
                QuickButton(Icons.Filled.Info, "Details") { onAct(handlers.onDetails) },
                QuickButton(Icons.Filled.EditNote, "Add instruction") { adding = !adding },
            )
            // Three per row so every label fits on one or two lines at normal and large font sizes.
            buttons.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { QuickActionButton(it.icon, it.label, Modifier.weight(1f), it.onClick) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (adding) {
                Spacer(Modifier.height(Orbit.spacing.md))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Instruction about $name") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    text = "Saved as an unclassified instruction about $name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Orbit.spacing.xs),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { adding = false; text = "" },
                        modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget),
                    ) { Text("Cancel") }
                    TextButton(
                        onClick = { val saved = text.trim(); onAct { handlers.onSaveInstruction(saved) } },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget),
                    ) { Text("Save") }
                }
            }
        }
    }
}

/** A tonal round icon button with its label underneath. The whole column is the tap target. */
@Composable
private fun QuickActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.xl)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Orbit.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(Orbit.sizing.touchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(Orbit.sizing.icon))
        }
        Spacer(Modifier.height(Orbit.spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
