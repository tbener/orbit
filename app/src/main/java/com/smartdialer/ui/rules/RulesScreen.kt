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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartdialer.ranking.TraceEntry
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams

class RulesActions(
    val addFreeText: (String) -> Unit,
    val updateFreeText: (Long, String) -> Unit,
    val deleteFreeText: (Long) -> Unit,
    val addTyped: (TypedRuleParams) -> Unit,
    val updateTyped: (Long, TypedRuleParams) -> Unit,
    val deleteTyped: (Long) -> Unit,
    val onBack: () -> Unit,
)

/** Which free-text rules the Rules page lists. */
enum class FreeTextFilter(val label: String) {
    All("All"),
    General("General"),
    ByContact("By contact"),
}

private sealed interface Pending {
    data class EditFreeText(val rule: FreeTextRule) : Pending
    data class DeleteFreeText(val rule: FreeTextRule) : Pending
    data object AddTyped : Pending
    data class EditTyped(val rule: TypedRule) : Pending
    data class DeleteTyped(val rule: TypedRule, val summary: String) : Pending
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(state: RulesUiState, actions: RulesActions) {
    var pending by remember { mutableStateOf<Pending?>(null) }
    var filter by rememberSaveable { mutableStateOf(FreeTextFilter.All) }
    val namesByKey = state.contacts?.associate { it.id to it.name }

    val shownFreeText = state.freeText.filter {
        when (filter) {
            FreeTextFilter.All -> true
            FreeTextFilter.General -> it.contactKey == null
            FreeTextFilter.ByContact -> it.contactKey != null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Rules", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) {
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
                    description = "Instructions in your own words, general or about one contact. The LLM interprets them and can get exact placement wrong.",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            item(key = "free-add") {
                AddFreeTextRow(
                    label = "Add a general instruction",
                    helper = "Saved as an unclassified instruction for all contacts. The LLM will sort it into a soft preference or a typed rule in a later stage. To add one about a single contact, open that contact's details.",
                    onAdd = actions.addFreeText,
                )
            }
            item(key = "free-filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FreeTextFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            if (shownFreeText.isEmpty()) {
                item(key = "free-empty") {
                    EmptyHint(if (state.freeText.isEmpty()) "No free-text rules yet." else "No free-text rules match this filter.")
                }
            }
            items(shownFreeText, key = { "f-${it.id}" }) { rule ->
                val key = rule.contactKey
                val name = key?.let { namesByKey?.get(it) }
                val notFound = key != null && namesByKey != null && name == null
                val scope = when {
                    key == null -> "General"
                    name != null -> "About $name"
                    notFound -> "About a contact that was not found"
                    else -> "About a contact"
                }
                RuleRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    text = rule.text,
                    detail = "$scope · ${rule.status.label()}",
                    detailIsWarning = false,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    accent = MaterialTheme.colorScheme.primary,
                    onEdit = { pending = Pending.EditFreeText(rule) },
                    onDelete = { pending = Pending.DeleteFreeText(rule) },
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
            item(key = "typed-add") {
                OutlinedButton(
                    onClick = { pending = Pending.AddTyped },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add typed rule")
                }
            }
            if (state.typed.isEmpty()) {
                item(key = "typed-empty") { EmptyHint("No typed rules yet.") }
            }
            items(state.typed, key = { "t-${it.id}" }) { rule ->
                val ref = rule.params.contact
                val currentName = namesByKey?.get(ref.lookupKey)
                val notFound = namesByKey != null && currentName == null
                val text = rule.params.describe(currentName ?: ref.displayName)
                RuleRow(
                    icon = if (rule.params is TypedRuleParams.PinToSlot) Icons.Filled.PushPin else Icons.Filled.VisibilityOff,
                    text = text,
                    detail = if (notFound) "Contact not found. This rule is skipped until the contact is back." else null,
                    detailIsWarning = notFound,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    accent = MaterialTheme.colorScheme.tertiary,
                    onEdit = { pending = Pending.EditTyped(rule) },
                    onDelete = { pending = Pending.DeleteTyped(rule, text) },
                )
            }

            item(key = "debug") { DebugTrace(state.trace, modifier = Modifier.padding(top = 16.dp)) }
        }
    }

    when (val action = pending) {
        null -> Unit
        is Pending.EditFreeText -> EditFreeTextDialog(
            rule = action.rule,
            onDismiss = { pending = null },
            onSave = { actions.updateFreeText(action.rule.id, it); pending = null },
        )
        is Pending.DeleteFreeText -> ConfirmDeleteDialog(
            summary = action.rule.text,
            onDismiss = { pending = null },
            onConfirm = { actions.deleteFreeText(action.rule.id); pending = null },
        )
        Pending.AddTyped -> TypedRuleDialog(
            initial = null,
            contacts = state.contacts,
            onDismiss = { pending = null },
            onSave = { actions.addTyped(it); pending = null },
        )
        is Pending.EditTyped -> TypedRuleDialog(
            initial = action.rule,
            contacts = state.contacts,
            onDismiss = { pending = null },
            onSave = { actions.updateTyped(action.rule.id, it); pending = null },
        )
        is Pending.DeleteTyped -> ConfirmDeleteDialog(
            summary = action.summary,
            onDismiss = { pending = null },
            onConfirm = { actions.deleteTyped(action.rule.id); pending = null },
        )
    }
}

@Composable
private fun DebugTrace(trace: List<TraceEntry>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Developer: last enforcement trace",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "What the deterministic layer did with the typed rules the last time the home list was built.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (trace.isEmpty()) {
                    Text("No typed rules have been applied yet.", style = MaterialTheme.typography.bodySmall)
                }
                trace.forEach { entry ->
                    Text(
                        text = "#${entry.ruleId}  ${entry.outcome}: ${entry.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
