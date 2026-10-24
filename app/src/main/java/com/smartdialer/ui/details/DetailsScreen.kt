package com.smartdialer.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Add
import com.smartdialer.data.Contact
import com.smartdialer.data.SignalSummary
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import com.smartdialer.ui.CollectUserMessages
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.UserMessage
import com.smartdialer.ui.components.EmptyState
import com.smartdialer.ui.components.InfoDialog
import com.smartdialer.ui.rules.AddRuleField
import com.smartdialer.ui.rules.ConfirmDeleteDialog
import com.smartdialer.ui.rules.EditFreeTextDialog
import com.smartdialer.ui.rules.FREE_TEXT_INFO
import com.smartdialer.ui.rules.GlassPanel
import com.smartdialer.ui.rules.RuleKind
import com.smartdialer.ui.rules.RuleRow
import com.smartdialer.ui.rules.ScreenHeader
import com.smartdialer.ui.rules.SectionHeader
import com.smartdialer.ui.rules.SolidButton
import com.smartdialer.ui.rules.TYPED_INFO
import com.smartdialer.ui.rules.TypedRuleDialog
import com.smartdialer.ui.rules.describe
import com.smartdialer.ui.rules.label
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground
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
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    messages: Flow<UserMessage>,
    nowMillis: Long,
    actions: DetailsActions,
) {
    var pending by remember { mutableStateOf<Pending?>(null) }
    var info by remember { mutableStateOf<RuleKind?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUserMessages(messages, snackbarHostState)

    val name = state.displayName
    val ref = ContactRef(state.contactKey, name)
    val notFound = state.contactsLoaded && state.contact == null

    OrbitBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenHeader(title = "Details", onBack = actions.onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = Orbit.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
                ) {
                    item(key = "header") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Orbit.spacing.xl, bottom = Orbit.spacing.sm)) {
                            val contact = state.contact ?: Contact(state.contactKey, name)
                            ContactAvatar(
                                contact = contact,
                                diameter = Orbit.sizing.avatarLarge,
                                initialsSp = 34,
                                colors = bubbleColorsFor(contact.id),
                                glow = 0.55f,
                            )
                            Spacer(Modifier.width(Orbit.spacing.lg))
                            Column(verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.semantics { heading() },
                                )
                                if (notFound) {
                                    Text(
                                        text = "Contact not found. Their rules are skipped until they are back.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Orbit.colors.alertText,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "summary") {
                        GlassPanel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = Orbit.colors.accent,
                                    modifier = Modifier.size(Orbit.sizing.iconSmall),
                                )
                                Spacer(Modifier.width(Orbit.spacing.sm))
                                Text("Recent activity", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                            }
                            SignalSummary.lines(name, state.signals, nowMillis).forEach {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    item(key = "instructions-header") {
                        SectionHeader(
                            kind = RuleKind.Soft,
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "About $name",
                            subtitle = "In your words. Only for this contact.",
                            onInfo = { info = RuleKind.Soft },
                            modifier = Modifier.padding(top = Orbit.spacing.md),
                        )
                    }
                    item(key = "instructions-add") {
                        AddRuleField(placeholder = "Add an instruction about $name", onAdd = actions.addInstruction)
                    }
                    if (state.instructions.isEmpty()) {
                        item(key = "instructions-empty") {
                            EmptyState(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                title = "Nothing yet",
                                body = "Try \"only call after 6pm\". Orbit keeps it with $name.",
                                compact = true,
                            )
                        }
                    }
                    items(state.instructions, key = { "i-${it.id}" }) { rule ->
                        RuleRow(
                            kind = RuleKind.Soft,
                            icon = Icons.Outlined.ChatBubbleOutline,
                            text = rule.text,
                            detail = rule.status.label(),
                            detailIsWarning = false,
                            onEdit = { pending = Pending.EditInstruction(rule) },
                            onDelete = { pending = Pending.DeleteInstruction(rule) },
                        )
                    }

                    item(key = "typed-header") {
                        SectionHeader(
                            kind = RuleKind.Solid,
                            icon = Icons.Filled.Rule,
                            title = "Hard rules",
                            subtitle = "Always obeyed for $name.",
                            onInfo = { info = RuleKind.Solid },
                            modifier = Modifier.padding(top = Orbit.spacing.xl),
                        )
                    }
                    item(key = "typed-add") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            SolidButton("Pin to slot", Icons.Filled.PushPin, { pending = Pending.AddTyped(TypedRuleType.PIN_TO_SLOT) }, Modifier.weight(1f))
                            SolidButton("Never suggest", Icons.Filled.VisibilityOff, { actions.addTyped(TypedRuleParams.NeverShow(ref)) }, Modifier.weight(1f))
                        }
                    }
                    if (state.typedRules.isEmpty()) {
                        item(key = "typed-empty") {
                            EmptyState(
                                icon = Icons.Filled.Rule,
                                title = "No hard rules",
                                body = "$name orbits freely.",
                                compact = true,
                            )
                        }
                    }
                    items(state.typedRules, key = { "t-${it.id}" }) { rule ->
                        val text = rule.params.describe(name)
                        RuleRow(
                            kind = RuleKind.Solid,
                            icon = if (rule.params is TypedRuleParams.PinToSlot) Icons.Filled.PushPin else Icons.Filled.VisibilityOff,
                            text = text,
                            detail = null,
                            detailIsWarning = false,
                            onEdit = { pending = Pending.EditTyped(rule) },
                            onDelete = { pending = Pending.DeleteTyped(rule, text) },
                        )
                    }
                }
            }
        }
    }

    info?.let { kind ->
        InfoDialog(
            title = if (kind == RuleKind.Soft) "Instructions" else "Hard rules",
            body = if (kind == RuleKind.Soft) FREE_TEXT_INFO else TYPED_INFO,
            onDismiss = { info = null },
        )
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
