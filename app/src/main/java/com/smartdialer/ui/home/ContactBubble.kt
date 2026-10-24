package com.smartdialer.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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

/**
 * Bubble sizes by priority. Larger and higher (more shadow) means higher priority. The top two tiers use
 * the stronger tone fill.
 */
enum class BubbleTier(val diameter: Dp, val initialsSp: Int, val badge: Dp, val elevation: Dp, val strongTone: Boolean) {
    Hero(120.dp, 38, 28.dp, Orbit.elevation.high, strongTone = true),
    Flank(80.dp, 26, 24.dp, Orbit.elevation.medium, strongTone = true),
    Medium(64.dp, 21, 22.dp, Orbit.elevation.low * 2, strongTone = false),
    Small(56.dp, 18, 20.dp, Orbit.elevation.low, strongTone = false),
}

/**
 * A contact bubble. When [onGesture] is null the bubble is display-only (used for the copy shown above
 * the dimmed screen behind the quick-actions card). The rect passed to [onGesture] is the bubble's
 * bounds in the window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactBubble(
    suggestion: Suggestion,
    rank: Int,
    tier: BubbleTier,
    selected: Boolean,
    onGesture: ((BubbleGesture, Rect) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = bubbleColorsFor(suggestion.contact.id, strong = tier.strongTone)
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
    // With a large font size the labels get a little more room and may wrap to two lines.
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val nameLines = if (largeFont) 2 else 1
    val labelWidth = maxOf(tier.diameter + if (largeFont) Orbit.spacing.lg else 0.dp, 72.dp)

    Column(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (onGesture == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        onClickLabel = "Open quick actions for $name",
                        role = Role.Button,
                        onLongClickLabel = "Open quick actions for $name",
                        onLongClick = { onGesture(BubbleGesture.LongPress, bounds) },
                        onClick = { onGesture(BubbleGesture.Tap, bounds) },
                    )
                },
            )
            .padding(Orbit.spacing.xs)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = if (selected) "Selected" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(tier.diameter)) {
            ContactAvatar(
                contact = suggestion.contact,
                diameter = tier.diameter,
                initialsSp = tier.initialsSp,
                colors = colors,
                elevation = tier.elevation,
            )
            // The selection ring sits above the photo.
            if (selected) {
                Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, CircleShape))
            }
            if (missed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Orbit.spacing.xxs, y = -Orbit.spacing.xxs)
                        .size(tier.badge)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneMissed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(tier.badge * 0.6f),
                    )
                }
            }
            if (suggestion.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = Orbit.spacing.xxs, y = Orbit.spacing.xxs)
                        .size(tier.badge)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(tier.badge * 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.height(Orbit.spacing.sm))
        CappedFontScale {
        Text(
            text = name,
            style = if (tier == BubbleTier.Hero) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = nameLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(labelWidth),
        )
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

/**
 * Bubble labels sit under fixed-size circles, so they grow with the user's font size only up to 150%
 * (the full text is always available in the quick-actions card and on the Details screen at any size).
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
        val scale = 0.85f + 0.15f * progress
        scaleX = scale
        scaleY = scale
    }
}
