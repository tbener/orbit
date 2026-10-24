package com.smartdialer.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.ui.components.GlassIconButton
import com.smartdialer.ui.components.InfoButton
import com.smartdialer.ui.components.OrbitTextField
import com.smartdialer.ui.components.SendButton
import com.smartdialer.ui.theme.CrispShape
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.SoftShape
import com.smartdialer.ui.theme.glass

// Building blocks shared by the Rules page and the contact Details screen.

/**
 * The two rule families look different on purpose. Free-text rules are read by the LLM, so they are soft
 * and fluid: round, translucent, violet-to-aqua. Typed rules are enforced by code, so they are crisp and
 * solid: square-cornered, opaque, with a hard teal edge.
 */
internal enum class RuleKind { Soft, Solid }

internal fun FreeTextStatus.label(): String = when (this) {
    FreeTextStatus.UNCLASSIFIED -> "Unclassified"
    FreeTextStatus.SOFT -> "Soft preference"
    FreeTextStatus.CONVERTED_TO_TYPED -> "Converted to a typed rule"
}

internal fun TypedRuleParams.describe(name: String): String = when (this) {
    is TypedRuleParams.PinToSlot -> "Always show $name in slot $slot"
    is TypedRuleParams.NeverShow -> "Never show $name"
}

internal const val FREE_TEXT_INFO =
    "Free-text rules are instructions in your own words, general or about one contact. The LLM reads them " +
        "and interprets them, so it can get exact placement wrong. New ones are saved as unclassified; " +
        "classification into soft preferences and typed rules arrives in a later stage."

internal const val TYPED_INFO =
    "Typed rules are hard rules, enforced by code after the LLM answers: pin a contact to a slot, or never " +
        "suggest them. They are always obeyed. If two rules conflict, the older one wins, and never-show " +
        "beats pin."

/** The title row of a screen: a glass back button and the title. */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Orbit.spacing.lg, vertical = Orbit.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
    ) {
        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, description = "Back", onClick = onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** A section header: an icon in the family's shape and color, the title, one line of subtitle and an info icon. */
@Composable
internal fun SectionHeader(
    kind: RuleKind,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Orbit.colors
    val accent = if (kind == RuleKind.Soft) colors.softAccent else colors.solidAccent
    val badgeShape: Shape = if (kind == RuleKind.Soft) CircleShape else CrispShape
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(Orbit.sizing.touchTarget - Orbit.spacing.sm)
                .then(
                    if (kind == RuleKind.Soft) {
                        Modifier.background(Brush.linearGradient(listOf(colors.softFillStart, colors.softFillEnd)), badgeShape)
                    } else {
                        Modifier.background(colors.solidFill, badgeShape).border(1.5.dp, accent, badgeShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Orbit.sizing.icon - Orbit.spacing.xs))
        }
        Spacer(Modifier.width(Orbit.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoButton(description = "About $title", onClick = onInfo)
    }
}

/** An inline add field: type, then send with the round accent button. */
@Composable
internal fun AddRuleField(placeholder: String, onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text.trim())
            text = ""
        }
    }
    OrbitTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = placeholder,
        modifier = modifier.fillMaxWidth(),
        shape = SoftShape,
        trailing = { SendButton(enabled = text.isNotBlank(), description = "Add rule", onClick = ::submit) },
    )
}

/** A crisp outlined button for typed rules. */
@Composable
internal fun SolidButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Orbit.colors.solidAccent
    Row(
        modifier = modifier
            .clip(CrispShape)
            .background(Orbit.colors.solidFill, CrispShape)
            .border(1.5.dp, accent, CrispShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Orbit.sizing.touchTarget)
            .padding(horizontal = Orbit.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Orbit.sizing.iconSmall))
        Text(label, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

/** One rule, soft or solid, with the rule text, an optional status line, edit and delete. */
@Composable
internal fun RuleRow(
    kind: RuleKind,
    icon: ImageVector,
    text: String,
    detail: String?,
    detailIsWarning: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Orbit.colors
    val soft = kind == RuleKind.Soft
    val shape = if (soft) SoftShape else CrispShape
    val accent = if (soft) colors.softAccent else colors.solidAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (soft) {
                    Modifier
                        .background(Brush.horizontalGradient(listOf(colors.softFillStart, colors.softFillEnd)), shape)
                        .border(Orbit.sizing.hairline, Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)), shape)
                } else {
                    Modifier
                        .background(colors.solidFill, shape)
                        .border(1.5.dp, accent.copy(alpha = 0.85f), shape)
                },
            )
            .clip(shape)
            .height(IntrinsicSize.Min)
            .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!soft) Box(Modifier.width(Orbit.spacing.xs).fillMaxHeight().background(accent))
        Spacer(Modifier.width(if (soft) Orbit.spacing.lg else Orbit.spacing.md))
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Orbit.sizing.icon - Orbit.spacing.xs))
        Spacer(Modifier.width(Orbit.spacing.md))
        Column(
            Modifier.weight(1f).padding(vertical = Orbit.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xxs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (soft) FontWeight.Normal else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detailIsWarning) colors.alertText else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (detailIsWarning) FontWeight.SemiBold else null,
                )
            }
        }
        RowIconButton(Icons.Filled.Edit, "Edit rule: $text", onEdit)
        RowIconButton(Icons.Filled.Delete, "Delete rule: $text", onDelete)
    }
}

@Composable
private fun RowIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Orbit.sizing.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Orbit.sizing.iconSmall + 2.dp))
    }
}

/** A glass card for read-only facts, such as a contact's recent activity. */
@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.large)
            .padding(Orbit.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs),
    ) { content() }
}
