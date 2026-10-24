package com.smartdialer.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionBadge
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.bubbleColorsFor
import kotlin.math.min

/** Initials size and glow for a bubble of a given size tier (0 is the top bubble). Bigger and brighter means higher priority. */
class OrbStyle(val initialsSp: Int, val badge: Dp, val glow: Float) {
    companion object {
        fun forRing(ring: Int): OrbStyle = when (ring) {
            0 -> OrbStyle(initialsSp = 44, badge = 30.dp, glow = 1f)
            1 -> OrbStyle(initialsSp = 26, badge = 22.dp, glow = 0.5f)
            2 -> OrbStyle(initialsSp = 22, badge = 20.dp, glow = 0.35f)
            3 -> OrbStyle(initialsSp = 20, badge = 19.dp, glow = 0.28f)
            else -> OrbStyle(initialsSp = 17, badge = 17.dp, glow = 0.2f)
        }
    }
}

/**
 * A contact bubble: the orb with its name (and, on the inner rings, a subtitle) underneath. When
 * [onGesture] is null the bubble is display-only (used for the lifted copy above the dimmed screen). The
 * rect passed to [onGesture] is the bubble's bounds in the window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactBubble(
    suggestion: Suggestion,
    rank: Int,
    ring: Int,
    diameter: Dp,
    labelWidth: Dp,
    showSubtitle: Boolean,
    selected: Boolean,
    onGesture: ((BubbleGesture, Rect) -> Unit)?,
    modifier: Modifier = Modifier,
    /** 0 to 1: how far the bubble has lifted forward (the quick-actions copy), which brightens its glow. */
    lift: Float = 0f,
) {
    val style = OrbStyle.forRing(ring)
    val colors = bubbleColorsFor(suggestion.contact.id)
    val name = suggestion.contact.name
    val missed = suggestion.badge == SuggestionBadge.MissedCall
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val description = buildString {
        append(name)
        append(", priority ").append(rank)
        suggestion.subtitle?.let { append(", ").append(it) }
        if (missed) append(", missed call")
        if (suggestion.isPinned) append(", pinned")
    }
    // With a large font the names may wrap, but stop growing at 150% so they stay under their orb.
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val nameLines = if (largeFont && ring <= 1) 2 else 1
    val nameStyle = when (ring) {
        0 -> MaterialTheme.typography.titleMedium
        1 -> MaterialTheme.typography.labelLarge
        else -> MaterialTheme.typography.labelMedium
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pressing a bubble squeezes it slightly. (No ripple: a clipped ripple would also cut off the glow.)
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, Orbit.motion.move(120), label = "press")

    Column(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (onGesture == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = "Open quick actions for $name",
                        role = Role.Button,
                        onLongClickLabel = "Open quick actions for $name",
                        onLongClick = { onGesture(BubbleGesture.LongPress, bounds) },
                        onClick = { onGesture(BubbleGesture.Tap, bounds) },
                    )
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = if (selected) "Selected" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(diameter)) {
            ContactAvatar(
                contact = suggestion.contact,
                diameter = diameter,
                initialsSp = style.initialsSp,
                colors = colors,
                glow = min(1f, style.glow + 0.7f * lift),
            )
            if (selected) {
                Box(Modifier.matchParentSize().border(2.dp, Orbit.colors.accent, CircleShape))
            }
            if (missed) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = Orbit.spacing.xxs, y = -Orbit.spacing.xxs),
                    size = style.badge,
                    color = Orbit.colors.alert,
                ) {
                    Icon(Icons.Filled.PhoneMissed, contentDescription = null, tint = Orbit.colors.onAlert, modifier = Modifier.size(style.badge * 0.6f))
                }
            }
            if (suggestion.isPinned) {
                Badge(
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = Orbit.spacing.xxs, y = Orbit.spacing.xxs),
                    size = style.badge,
                    color = Orbit.colors.accent,
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = Orbit.colors.onAccent, modifier = Modifier.size(style.badge * 0.6f))
                }
            }
        }
        Spacer(Modifier.height(if (ring == 0) Orbit.spacing.sm else Orbit.spacing.xs))
        CappedFontScale {
            Text(
                text = name,
                style = nameStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(labelWidth),
            )
            if (showSubtitle) {
                suggestion.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (missed) Orbit.colors.alertText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (missed) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(labelWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(modifier: Modifier, size: Dp, color: Color, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(size)
            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Bubble labels sit under fixed-size orbs, so they grow with the user's font size only up to 150% (the
 * full text is always available in the quick-actions caption and on the Details screen at any size).
 */
@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, MAX_LABEL_FONT_SCALE)),
        content = content,
    )
}

private const val MAX_LABEL_FONT_SCALE = 1.5f

/**
 * Fades and scales a bubble in the first time it appears, staggered by its position. When [play] is
 * false (it has been seen before) or system animations are off, it is simply there.
 */
@Composable
fun Modifier.bubbleEntrance(play: Boolean, index: Int): Modifier {
    val motion = Orbit.motion
    var started by remember { mutableStateOf(!play) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = motion.enter(durationMillis = motion.longMillis, delayMillis = min(index, motion.bubbleStaggerLimit) * motion.bubbleStaggerMillis),
        label = "bubbleEntrance",
    )
    return graphicsLayer {
        alpha = progress
        val scale = 0.8f + 0.2f * progress
        scaleX = scale
        scaleY = scale
    }
}
