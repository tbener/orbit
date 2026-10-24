package com.smartdialer.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.smartdialer.ranking.TraceEntry
import com.smartdialer.llm.InstructionLimits
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.FreeTextStatus
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.ui.components.EmptyState
import com.smartdialer.ui.components.InfoDialog
import com.smartdialer.ui.components.OrbitChip
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground

class RulesActions(
    val addFreeText: (String) -> Unit,
    val updateFreeText: (Long, String) -> Unit,
    val deleteFreeText: (Long) -> Unit,
    val addTyped: (TypedRuleParams) -> Unit,
    val updateTyped: (Long, TypedRuleParams) -> Unit,
    val deleteTyped: (Long) -> Unit,
    /** Turns a free-text rule into this typed rule (the user's override of "soft" or "needs review"). */
    val makeHard: (freeTextId: Long, TypedRuleParams) -> Unit,
    /** Deletes the typed rule a free-text rule was converted into and keeps the rule as a soft instruction. */
    val makeSoft: (freeTextId: Long, typedRuleId: Long) -> Unit,
    /** Keeps a rule that needed review as a soft instruction. */
    val keepSoft: (freeTextId: Long) -> Unit,
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
    data class MakeHard(val rule: FreeTextRule) : Pending
    data class EditTyped(val rule: TypedRule) : Pending
    data class DeleteTyped(val rule: TypedRule, val summary: String) : Pending
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(state: RulesUiState, actions: RulesActions) {
    var pending by remember { mutableStateOf<Pending?>(null) }
    var info by remember { mutableStateOf<RuleKind?>(null) }
    var filter by rememberSaveable { mutableStateOf(FreeTextFilter.All) }
    val namesByKey = state.contacts?.associate { it.id to it.name }

    val shownFreeText = state.freeText.filter {
        when (filter) {
            FreeTextFilter.All -> true
            FreeTextFilter.General -> it.contactKey == null
            FreeTextFilter.ByContact -> it.contactKey != null
        }
    }

    OrbitBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenHeader(title = "Rules", onBack = actions.onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = Orbit.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
                ) {
                    item(key = "free-header") {
                        SectionHeader(
                            kind = RuleKind.Soft,
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "Free-text rules",
                            subtitle = "In your own words. The LLM interprets them.",
                            onInfo = { info = RuleKind.Soft },
                        )
                    }
                    item(key = "free-add") {
                        AddRuleField(placeholder = "Add a general instruction", onAdd = actions.addFreeText)
                    }
                    if (state.unusedInstructions > 0) {
                        item(key = "free-limit") {
                            Text(
                                "Only the ${InstructionLimits.MAX_PER_REQUEST} most recent instructions are used for ranking. " +
                                    "${state.unusedInstructions} older one${if (state.unusedInstructions == 1) " is" else "s are"} kept but not used. Delete some to use them again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Orbit.colors.alertText,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    item(key = "free-filter") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs),
                        ) {
                            FreeTextFilter.entries.forEach { option ->
                                OrbitChip(label = option.label, selected = filter == option, onClick = { filter = option })
                            }
                        }
                    }
                    if (shownFreeText.isEmpty()) {
                        item(key = "free-empty") {
                            if (state.freeText.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Outlined.ChatBubbleOutline,
                                    title = "Nothing to interpret yet",
                                    body = "Tell Orbit how you like to call, like \"ignore missed calls\".",
                                    compact = true,
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Outlined.ChatBubbleOutline,
                                    title = "Nothing here",
                                    body = "No rules match this filter. Try All.",
                                    compact = true,
                                )
                            }
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
                        val linked = rule.typedRuleId?.let { id -> state.typed.firstOrNull { it.id == id } }
                        RuleRow(
                            kind = RuleKind.Soft,
                            icon = Icons.Outlined.ChatBubbleOutline,
                            text = rule.text,
                            detail = "$scope · ${rule.status.label()}",
                            detailIsWarning = rule.status == FreeTextStatus.NEEDS_REVIEW,
                            explanation = rule.explanation,
                            onEdit = { pending = Pending.EditFreeText(rule) },
                            onDelete = { pending = Pending.DeleteFreeText(rule) },
                            footer = {
                                Column {
                                    if (linked != null) {
                                        val linkedName = namesByKey?.get(linked.params.contact.lookupKey) ?: linked.params.contact.displayName
                                        Text(
                                            text = "Enforced as: ${linked.params.describe(linkedName)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Orbit.colors.solidAccent,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
                                        when (rule.status) {
                                            FreeTextStatus.CONVERTED_TO_TYPED ->
                                                rule.typedRuleId?.let { typedId ->
                                                    RuleActionButton("Make soft", RuleKind.Soft) { actions.makeSoft(rule.id, typedId) }
                                                }
                                            FreeTextStatus.NEEDS_REVIEW -> {
                                                RuleActionButton("Choose contact", RuleKind.Solid) { pending = Pending.MakeHard(rule) }
                                                RuleActionButton("Keep soft", RuleKind.Soft) { actions.keepSoft(rule.id) }
                                            }
                                            FreeTextStatus.REJECTED_OFF_TOPIC -> Unit
                                            FreeTextStatus.SOFT, FreeTextStatus.UNCLASSIFIED ->
                                                RuleActionButton("Make hard", RuleKind.Solid) { pending = Pending.MakeHard(rule) }
                                        }
                                    }
                                }
                            },
                        )
                    }

                    item(key = "typed-header") {
                        SectionHeader(
                            kind = RuleKind.Solid,
                            icon = Icons.Filled.Rule,
                            title = "Typed rules",
                            subtitle = "Hard rules. Always obeyed.",
                            onInfo = { info = RuleKind.Solid },
                            modifier = Modifier.padding(top = Orbit.spacing.xl),
                        )
                    }
                    item(key = "typed-add") {
                        SolidButton(
                            label = "Add typed rule",
                            icon = Icons.Filled.Add,
                            onClick = { pending = Pending.AddTyped },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.typed.isEmpty()) {
                        item(key = "typed-empty") {
                            EmptyState(
                                icon = Icons.Filled.Rule,
                                title = "No hard rules",
                                body = "Everyone orbits freely.",
                                compact = true,
                            )
                        }
                    }
                    items(state.typed, key = { "t-${it.id}" }) { rule ->
                        val ref = rule.params.contact
                        val currentName = namesByKey?.get(ref.lookupKey)
                        val notFound = namesByKey != null && currentName == null
                        val text = rule.params.describe(currentName ?: ref.displayName)
                        val source = rule.sourceFreeTextRuleId?.let { id -> state.freeText.firstOrNull { it.id == id } }
                        RuleRow(
                            kind = RuleKind.Solid,
                            icon = if (rule.params is TypedRuleParams.PinToSlot) Icons.Filled.PushPin else Icons.Filled.VisibilityOff,
                            text = text,
                            detail = when {
                                notFound -> "Contact not found. Skipped until they are back."
                                source != null -> "Made from your instruction: \u201C${source.text}\u201D"
                                else -> null
                            },
                            detailIsWarning = notFound,
                            onEdit = { pending = Pending.EditTyped(rule) },
                            onDelete = { pending = Pending.DeleteTyped(rule, text) },
                        )
                    }

                    item(key = "debug") { DebugTrace(state.trace, modifier = Modifier.padding(top = Orbit.spacing.xl)) }
                }
            }
        }
    }

    info?.let { kind ->
        InfoDialog(
            title = if (kind == RuleKind.Soft) "Free-text rules" else "Typed rules",
            body = if (kind == RuleKind.Soft) FREE_TEXT_INFO else TYPED_INFO,
            onDismiss = { info = null },
        )
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
        is Pending.MakeHard -> {
            val rule = action.rule
            val fixedName = rule.contactKey?.let { namesByKey?.get(it) }
            TypedRuleDialog(
                initial = null,
                contacts = state.contacts,
                fixedContact = if (rule.contactKey != null && fixedName != null) ContactRef(rule.contactKey, fixedName) else null,
                initialType = rule.proposedType,
                initialSlot = rule.proposedSlot,
                onDismiss = { pending = null },
                onSave = { actions.makeHard(rule.id, it); pending = null },
            )
        }
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs)) {
        Text(
            text = "Developer: last enforcement trace",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        GlassPanel {
            if (trace.isEmpty()) {
                Text(
                    "No typed rules have been applied yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trace.forEach { entry ->
                Text(
                    text = "#${entry.ruleId}  ${entry.outcome}: ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
