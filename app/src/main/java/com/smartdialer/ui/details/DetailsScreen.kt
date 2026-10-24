package com.smartdialer.ui.details

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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartdialer.data.SignalSummary
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import com.smartdialer.ui.CollectUserMessages
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.UserMessage
import com.smartdialer.ui.rules.AddFreeTextRow
import com.smartdialer.ui.rules.ConfirmDeleteDialog
import com.smartdialer.ui.rules.EditFreeTextDialog
import com.smartdialer.ui.rules.EmptyHint
import com.smartdialer.ui.rules.RuleRow
import com.smartdialer.ui.rules.SectionHeader
import com.smartdialer.ui.rules.TypedRuleDialog
import com.smartdialer.ui.rules.describe
import com.smartdialer.ui.rules.label
import com.smartdialer.ui.theme.bubbleColorsFor
import kotlinx.coroutines.flow.Flow

class DetailsActions(
    val addInstruction: (String) -> Unit,
    val updateInstruction: (Long, String) -> Unit,
    val deleteInstruction: (Long) -> Unit,
    val addTyped: (TypedRuleParams) -> Unit,
    val updateTyped: (Long, TypedRuleParams) -> Unit,
    val deleteTyped: (Long) -> Unit,
    val onBack: () -> Unit,
)

private sealed interface Pending {
    data class EditInstruction(val rule: FreeTextRule) : Pending
    data class DeleteInstruction(val rule: FreeTextRule) : Pending
    data class AddTyped(val type: TypedRuleType) : Pending
    data class EditTyped(val rule: TypedRule) : Pending
    data class DeleteTyped(val rule: TypedRule, val summary: String) : Pending
}

/** Orbit's page for one contact: recent activity, their instructions and their typed rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    messages: Flow<UserMessage>,
    nowMillis: Long,
    actions: DetailsActions,
) {
    var pending by remember { mutableStateOf<Pending?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUserMessages(messages, snackbarHostState)

    val name = state.displayName
    val ref = ContactRef(state.contactKey, name)
    val notFound = state.contactsLoaded && state.contact == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Details", fontWeight = FontWeight.SemiBold) },
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
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val contact = state.contact
                    if (contact != null) {
                        ContactAvatar(contact, diameter = 72.dp, initialsSp = 26, colors = bubbleColorsFor(contact.id))
                    } else {
                        ContactAvatar(
                            com.smartdialer.data.Contact(state.contactKey, name),
                            diameter = 72.dp,
                            initialsSp = 26,
                            colors = bubbleColorsFor(state.contactKey),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (notFound) {
                            Text(
                                text = "Contact not found. Rules below are skipped until the contact is back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            item(key = "summary") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recent activity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        SignalSummary.lines(name, state.signals, nowMillis).forEach {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item(key = "instructions-header") {
                SectionHeader(
                    title = "Instructions for $name",
                    tag = "Read by the LLM",
                    description = "In your own words. Only used for this contact.",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "instructions-add") {
                AddFreeTextRow(
                    label = "Add an instruction about $name",
                    helper = "Saved as an unclassified instruction. Classification arrives in a later stage.",
                    onAdd = actions.addInstruction,
                )
            }
            if (state.instructions.isEmpty()) {
                item(key = "instructions-empty") { EmptyHint("No instructions about $name yet.") }
            }
            items(state.instructions, key = { "i-${it.id}" }) { rule ->
                RuleRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    text = rule.text,
                    detail = rule.status.label(),
                    detailIsWarning = false,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    accent = MaterialTheme.colorScheme.primary,
                    onEdit = { pending = Pending.EditInstruction(rule) },
                    onDelete = { pending = Pending.DeleteInstruction(rule) },
                )
            }

            item(key = "typed-header") {
                SectionHeader(
                    title = "Typed rules for $name",
                    tag = "Enforced by code",
                    description = "Hard rules applied after the LLM answers.",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            item(key = "typed-add") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pending = Pending.AddTyped(TypedRuleType.PIN_TO_SLOT) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Pin to slot…") }
                    OutlinedButton(
                        onClick = { actions.addTyped(TypedRuleParams.NeverShow(ref)) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Never suggest") }
                }
            }
            if (state.typedRules.isEmpty()) {
                item(key = "typed-empty") { EmptyHint("No typed rules for $name yet.") }
            }
            items(state.typedRules, key = { "t-${it.id}" }) { rule ->
                val text = rule.params.describe(name)
                RuleRow(
                    icon = if (rule.params is TypedRuleParams.PinToSlot) Icons.Filled.PushPin else Icons.Filled.VisibilityOff,
                    text = text,
                    detail = null,
                    detailIsWarning = false,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    accent = MaterialTheme.colorScheme.tertiary,
                    onEdit = { pending = Pending.EditTyped(rule) },
                    onDelete = { pending = Pending.DeleteTyped(rule, text) },
                )
            }
        }
    }

    when (val action = pending) {
        null -> Unit
        is Pending.EditInstruction -> EditFreeTextDialog(
            rule = action.rule,
            onDismiss = { pending = null },
            onSave = { actions.updateInstruction(action.rule.id, it); pending = null },
        )
        is Pending.DeleteInstruction -> ConfirmDeleteDialog(
            summary = action.rule.text,
            onDismiss = { pending = null },
            onConfirm = { actions.deleteInstruction(action.rule.id); pending = null },
        )
        is Pending.AddTyped -> TypedRuleDialog(
            initial = null,
            contacts = null,
            fixedContact = ref,
            initialType = action.type,
            onDismiss = { pending = null },
            onSave = { actions.addTyped(it); pending = null },
        )
        is Pending.EditTyped -> TypedRuleDialog(
            initial = action.rule,
            contacts = null,
            fixedContact = ref,
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
