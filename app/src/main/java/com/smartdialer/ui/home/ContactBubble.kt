package com.smartdialer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.data.Suggestion
import com.smartdialer.data.SuggestionBadge
import com.smartdialer.ui.theme.bubbleColorsFor

/** Bubble sizes by priority. Larger means higher priority. */
enum class BubbleTier(val diameter: Dp, val initialsSp: Int, val badge: Dp) {
    Hero(120.dp, 38, 28.dp),
    Flank(80.dp, 26, 24.dp),
    Medium(64.dp, 21, 22.dp),
    Small(56.dp, 18, 20.dp),
}

@Composable
fun ContactBubble(
    suggestion: Suggestion,
    rank: Int,
    tier: BubbleTier,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = bubbleColorsFor(suggestion.contact.id)
    val name = suggestion.contact.name
    val description = buildString {
        append(name)
        append(", priority ").append(rank)
        suggestion.subtitle?.let { append(", ").append(it) }
        if (suggestion.badge == SuggestionBadge.MissedCall) append(", missed call")
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Show why $name is suggested", role = Role.Button, onClick = onClick)
            .padding(4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = if (selected) "Selected" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(tier.diameter)) {
            Box(
                modifier = Modifier
                    .size(tier.diameter)
                    .clip(CircleShape)
                    .background(colors.container)
                    .then(
                        when {
                            selected -> Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            colors.outlined -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            else -> Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsOf(name),
                    color = colors.content,
                    fontSize = tier.initialsSp.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (suggestion.badge == SuggestionBadge.MissedCall) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
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
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (tier == BubbleTier.Hero) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(maxOf(tier.diameter, 72.dp)),
        )
        suggestion.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(maxOf(tier.diameter, 72.dp)),
            )
        }
    }
}

fun initialsOf(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words.first().first()}${words.last().first()}"
    }.uppercase()
}
