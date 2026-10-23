package com.smartdialer.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.TypedRule

private sealed interface PendingAction {
    data class EditFreeText(val rule: FreeTextRule) : PendingAction
    data class DeleteFreeText(val rule: FreeTextRule) : PendingAction
    data class EditTyped(val rule: TypedRule) : PendingAction
    data class DeleteTyped(val rule: TypedRule) : PendingAction
}

private fun TypedRule.describe(): String = when (this) {
    is TypedRule.Pin -> "Always show $contactName in slot $slot"
    is TypedRule.Hide -> "Never show $contactName"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    freeTextRules: List<FreeTextRule>,
    typedRules: List<TypedRule>,
    onUpdateFreeText: (FreeTextRule) -> Unit,
    onDeleteFreeText: (String) -> Unit,
    onUpdateTyped: (TypedRule) -> Unit,
    onDeleteTyped: (String) -> Unit,
    onBack: () -> Unit,
) {
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Rules", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "free-header") {
                SectionHeader(
                    title = "Free-text rules",
                    tag = "Read by the LLM",
                    description = "Preferences in your own words. The LLM interprets them and can get exact placement wrong.",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (freeTextRules.isEmpty()) {
                item(key = "free-empty") { EmptyHint("No free-text rules yet.") }
            }
            items(freeTextRules, key = { "f-${it.id}" }) { rule ->
                RuleRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    text = rule.text,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    accent = MaterialTheme.colorScheme.primary,
                    onEdit = { pending = PendingAction.EditFreeText(rule) },
                    onDelete = { pending = PendingAction.DeleteFreeText(rule) },
                )
            }

            item(key = "typed-header") {
                SectionHeader(
                    title = "Typed rules",
                    tag = "Enforced by code",
                    description = "Hard rules applied after the LLM answers. These are always obeyed.",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (typedRules.isEmpty()) {
                item(key = "typed-empty") { EmptyHint("No typed rules yet.") }
            }
            items(typedRules, key = { "t-${it.id}" }) { rule ->
                RuleRow(
                    icon = if (rule is TypedRule.Pin) Icons.Filled.PushPin else Icons.Filled.VisibilityOff,
                    text = rule.describe(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    accent = MaterialTheme.colorScheme.tertiary,
                    onEdit = { pending = PendingAction.EditTyped(rule) },
                    onDelete = { pending = PendingAction.DeleteTyped(rule) },
                )
            }
        }
    }

    when (val action = pending) {
        null -> Unit
        is PendingAction.EditFreeText -> EditFreeTextDialog(
            rule = action.rule,
            onDismiss = { pending = null },
            onSave = { onUpdateFreeText(it); pending = null },
        )
        is PendingAction.EditTyped -> EditTypedRuleDialog(
            rule = action.rule,
            onDismiss = { pending = null },
            onSave = { onUpdateTyped(it); pending = null },
        )
        is PendingAction.DeleteFreeText -> ConfirmDeleteDialog(
            summary = action.rule.text,
            onDismiss = { pending = null },
            onConfirm = { onDeleteFreeText(action.rule.id); pending = null },
        )
        is PendingAction.DeleteTyped -> ConfirmDeleteDialog(
            summary = action.rule.describe(),
            onDismiss = { pending = null },
            onConfirm = { onDeleteTyped(action.rule.id); pending = null },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    tag: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(12.dp))
            Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun RuleRow(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    accent: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = containerColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit rule: $text")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete rule: $text")
            }
        }
    }
}

@Composable
private fun EditFreeTextDialog(
    rule: FreeTextRule,
    onDismiss: () -> Unit,
    onSave: (FreeTextRule) -> Unit,
) {
    var text by remember { mutableStateOf(rule.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit free-text rule") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Rule") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(rule.copy(text = text.trim())) },
                enabled = text.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditTypedRuleDialog(
    rule: TypedRule,
    onDismiss: () -> Unit,
    onSave: (TypedRule) -> Unit,
) {
    var name by remember { mutableStateOf(rule.contactName) }
    var slotText by remember { mutableStateOf((rule as? TypedRule.Pin)?.slot?.toString().orEmpty()) }
    val slot = slotText.toIntOrNull()
    val valid = name.isNotBlank() && (rule !is TypedRule.Pin || (slot != null && slot >= 1))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule is TypedRule.Pin) "Edit pin rule" else "Edit hide rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rule is TypedRule.Pin) {
                    OutlinedTextField(
                        value = slotText,
                        onValueChange = { value -> slotText = value.filter { it.isDigit() }.take(2) },
                        label = { Text("Slot (1 = top)") },
                        singleLine = true,
                        isError = slot == null || slot < 1,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = when (rule) {
                        is TypedRule.Pin -> rule.copy(contactName = name.trim(), slot = slot ?: rule.slot)
                        is TypedRule.Hide -> rule.copy(contactName = name.trim())
                    }
                    onSave(updated)
                },
                enabled = valid,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    summary: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this rule?") },
        text = { Text(summary) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        },
    )
}
