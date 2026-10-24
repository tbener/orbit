package com.smartdialer.ui.home

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Suggestion
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

private const val EXIT_MILLIS = 120
private const val ENTER_MILLIS = 150

/** True when the user has turned system animations off. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

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
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else if (shown) ENTER_MILLIS else EXIT_MILLIS),
        label = "quickActions",
    )

    fun close() {
        scope.launch {
            shown = false
            if (!reducedMotion) delay(EXIT_MILLIS.toLong())
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
                .background(Color.Black.copy(alpha = 0.32f))
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
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val bounds = IntRect(
            left = insets.getLeft(this, layoutDirection) + margin,
            top = insets.getTop(this) + margin,
            right = constraints.maxWidth - insets.getRight(this, layoutDirection) - margin,
            bottom = constraints.maxHeight - insets.getBottom(this) - margin,
        )
        val card = measurables.first().measure(
            Constraints(maxWidth = bounds.width.coerceAtLeast(0), maxHeight = bounds.height.coerceAtLeast(0)),
        )
        val offset = PopupPlacement.place(anchor, IntSize(card.width, card.height), bounds, gap = 12.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(offset) }
    }
}

@Composable
private fun QuickActionsCard(
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .width(304.dp)
            // Taps inside the card must not fall through to the dismissing scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "Quick actions for $name" },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Instruction about $name") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    text = "Saved as an unclassified instruction about $name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { adding = false; text = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                    TextButton(
                        onClick = { val saved = text.trim(); onAct { handlers.onSaveInstruction(saved) } },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
