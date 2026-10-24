package com.smartdialer.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.PillShape

// Building blocks shared by the Rules page and the contact Details screen.

internal fun FreeTextStatus.label(): String = when (this) {
    FreeTextStatus.UNCLASSIFIED -> "Unclassified (classification arrives in a later stage)"
    FreeTextStatus.SOFT -> "Soft preference"
    FreeTextStatus.CONVERTED_TO_TYPED -> "Converted to a typed rule"
}

internal fun TypedRuleParams.describe(name: String): String = when (this) {
    is TypedRuleParams.PinToSlot -> "Always show $name in slot $slot"
    is TypedRuleParams.NeverShow -> "Never show $name"
}

/**
 * A section header: an icon in the section's color, the title, a tag saying who reads the rules, and a
 * description. The icon, tag and tint together make the two rule lists distinguishable without color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionHeader(
    icon: ImageVector,
    title: String,
    tag: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
        // The tag drops under the title when there is no room beside it (large fonts, narrow screens).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(Orbit.sizing.icon + Orbit.spacing.sm).background(containerColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(Orbit.sizing.iconSmall))
                }
                Spacer(Modifier.width(Orbit.spacing.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Orbit.spacing.sm, vertical = Orbit.spacing.xs),
                )
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AddFreeTextRow(label: String, helper: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    // With a large font the field and the button stack, so the label is not squeezed.
    val stacked = LocalDensity.current.fontScale > 1.3f
    val field: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            shape = MaterialTheme.shapes.medium,
            modifier = modifier,
        )
    }
    val button: @Composable (Modifier) -> Unit = { modifier ->
        Button(
            onClick = { onAdd(text); text = "" },
            enabled = text.isNotBlank(),
            shape = PillShape,
            modifier = modifier.heightIn(min = Orbit.sizing.touchTarget),
        ) { Text("Add rule") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
        if (stacked) {
            field(Modifier.fillMaxWidth())
            button(Modifier.align(Alignment.End))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                field(Modifier.weight(1f))
                button(Modifier)
            }
        }
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One rule: a colored edge and icon for its section, the rule text, an optional status line, edit and delete. */
@Composable
internal fun RuleRow(
    icon: ImageVector,
    text: String,
    detail: String?,
    detailIsWarning: Boolean,
    containerColor: Color,
    accent: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = containerColor, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min).heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(Orbit.spacing.xs).fillMaxHeight().background(accent))
            Spacer(Modifier.width(Orbit.spacing.md))
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Orbit.sizing.iconSmall + 2.dp))
            Spacer(Modifier.width(Orbit.spacing.md))
            Column(
                Modifier.weight(1f).padding(vertical = Orbit.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xxs),
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (detailIsWarning) Orbit.colors.alertText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (detailIsWarning) FontWeight.SemiBold else null,
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(Orbit.sizing.touchTarget)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit rule: $text", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(Orbit.sizing.touchTarget)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete rule: $text", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
