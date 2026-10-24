package com.smartdialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.smartdialer.ui.theme.Orbit

/**
 * An empty list: an icon, a short title and one sentence that says what to do next.
 * [compact] is the smaller version used inside a section that has other content around it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val circle = if (compact) Orbit.sizing.avatarMedium + Orbit.spacing.sm else Orbit.sizing.avatarLarge - Orbit.spacing.lg
    val glyph = if (compact) Orbit.sizing.icon + Orbit.spacing.xs else Orbit.sizing.emptyStateIcon
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Orbit.spacing.lg, vertical = if (compact) Orbit.spacing.lg else Orbit.spacing.xl)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(circle)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(glyph),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
