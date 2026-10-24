package com.smartdialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.PillShape
import com.smartdialer.ui.theme.glass
import com.smartdialer.ui.theme.glassCircle

/** A round frosted-glass icon button. At least 48dp, with a spoken [description]. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(Orbit.sizing.touchTarget)
            .glassCircle(elevation = Orbit.elevation.low)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Orbit.sizing.icon))
    }
}

/**
 * The text field used everywhere: a glass shape with a placeholder, optional leading and trailing
 * content, and an accent edge when focused. [label] is what a screen reader announces.
 */
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    shape: Shape = MaterialTheme.shapes.medium,
    singleLine: Boolean = false,
    minLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** Longest text accepted. Typing stops there and a short message says why. */
    maxLength: Int? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = Orbit.colors
    BasicTextField(
        value = value,
        onValueChange = { text -> onValueChange(if (maxLength != null) text.take(maxLength) else text) },
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(colors.accent),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
          Column {
            Row(
                modifier = Modifier
                    .glass(shape)
                    .then(if (focused) Modifier.border(1.5.dp, colors.accent, shape) else Modifier)
                    .heightIn(min = Orbit.sizing.touchTarget)
                    .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Box(Modifier.size(Orbit.spacing.sm))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        )
                    }
                    inner()
                }
                if (trailing != null) {
                    Box(Modifier.size(Orbit.spacing.sm))
                    trailing()
                }
            }
            if (maxLength != null && value.length >= maxLength) {
                Text(
                    text = "Limit reached: $maxLength characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Orbit.colors.alertText,
                    modifier = Modifier.padding(start = Orbit.spacing.lg, top = Orbit.spacing.xs),
                )
            }
          }
        },
    )
}

/** The accent-filled round send button that goes at the end of an inline add field. */
@Composable
fun SendButton(enabled: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Orbit.colors
    Box(
        modifier = modifier
            .size(Orbit.sizing.touchTarget - Orbit.spacing.sm)
            .clip(CircleShape)
            .background(
                if (enabled) Brush.linearGradient(listOf(colors.accentStart, colors.accentEnd)) else SolidColor(colors.glassFillStrong),
            )
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Send,
            contentDescription = null,
            tint = if (enabled) colors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Orbit.sizing.iconSmall),
        )
    }
}

/** A pill option. Selected options get an accent edge and a faint accent fill. */
@Composable
fun OrbitChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Orbit.colors
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(listOf(colors.accentStart.copy(alpha = 0.22f), colors.accentEnd.copy(alpha = 0.22f))),
                            PillShape,
                        )
                        .border(1.5.dp, colors.accent, PillShape)
                } else {
                    Modifier.glass(PillShape)
                },
            )
            .padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A small info icon that the caller wires to an [InfoDialog]. */
@Composable
fun InfoButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Orbit.sizing.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Orbit.sizing.iconSmall + 2.dp),
        )
    }
}

/** The longer explanation that sits behind an info icon. */
@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Got it") }
        },
    )
}

/** A frosted-glass card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    strong: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.glass(shape, strong, Orbit.elevation.low).clip(shape)) { content() }
}
