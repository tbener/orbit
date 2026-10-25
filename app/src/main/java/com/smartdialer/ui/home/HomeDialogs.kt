package com.smartdialer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.theme.bubbleColorsFor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.smartdialer.data.Contact
import com.smartdialer.llm.InstructionLimits
import com.smartdialer.ui.components.OrbitTextField
import com.smartdialer.ui.components.SendButton
import com.smartdialer.ui.theme.Orbit

enum class NumberPurpose(val verb: String) {
    Call("Call"),
    WhatsApp("WhatsApp"),
}

/** A contact with several numbers where none clearly wins; the user chooses. */
class NumberPick(val contact: Contact, val purpose: NumberPurpose, val candidates: List<String>)

@Composable
fun NumberPickerDialog(
    pick: NumberPick,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("${pick.purpose.verb} ${pick.contact.name}: which number?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                pick.candidates.forEach { number ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.sm)
                            .clickable { onPick(number) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(number, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Cancel") }
        },
    )
}

/** Bottom sheet for adding an instruction that applies to all contacts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralInstructionSheet(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Orbit.spacing.xl, vertical = Orbit.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
        ) {
            Text("General instruction", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Applies to everyone, like \"ignore missed calls\". Saved as it is; you can edit or delete it on the Rules page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OrbitTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Instruction for all contacts",
                maxLength = InstructionLimits.MAX_LENGTH,
                minLines = 3,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                trailing = { SendButton(enabled = text.isNotBlank(), description = "Save general instruction", onClick = { onSave(text.trim()) }) },
            )
        }
    }
}

/** A small note on contacts that a never-show rule keeps out of the suggestions. */
@Composable
fun HiddenLabel() {
    Text(
        text = "Hidden from suggestions",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The quick actions for a contact that has no bubble to anchor to (a search result): a bottom sheet with the
 * same action list as the bubble popup, including the inline "Add instruction" field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactActionsSheet(
    contact: Contact,
    hidden: Boolean,
    actions: List<QuickAction>,
    onDismiss: () -> Unit,
) {
    var inputFor by remember { mutableStateOf<QuickActionKind.Input?>(null) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(inputFor) { if (inputFor != null) focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Orbit.spacing.xl, vertical = Orbit.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.lg)) {
                ContactAvatar(contact, diameter = Orbit.sizing.avatarMedium, initialsSp = 18, colors = bubbleColorsFor(contact.id), glow = 0.2f)
                Column {
                    Text(contact.name, style = MaterialTheme.typography.titleLarge)
                    if (hidden) HiddenLabel()
                }
            }
            val input = inputFor
            if (input == null) {
                actions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Orbit.sizing.touchTarget)
                            .clickable(role = Role.Button, onClickLabel = action.description) {
                                when (val kind = action.kind) {
                                    is QuickActionKind.Run -> { onDismiss(); kind.onClick() }
                                    is QuickActionKind.Input -> inputFor = kind
                                }
                            }
                            .padding(vertical = Orbit.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.lg),
                    ) {
                        Box(
                            Modifier.size(Orbit.sizing.touchTarget).clip(CircleShape).background(actionBrush(action.tone)),
                            contentAlignment = Alignment.Center,
                        ) { ActionGlyph(action, Modifier.size(26.dp)) }
                        Text(action.description, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                OrbitTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Instruction about ${contact.name}",
                    maxLength = InstructionLimits.MAX_LENGTH,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    trailing = {
                        SendButton(
                            enabled = text.isNotBlank(),
                            description = "Save instruction about ${contact.name}",
                            onClick = {
                                onDismiss()
                                input.onSave(text.trim())
                            },
                        )
                    },
                )
            }
        }
    }
}

/** The accessible alternative to dragging: pick the slot to move [name] to. Slots held by a pin cannot be chosen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SlotPickerDialog(
    name: String,
    current: Int,
    count: Int,
    pinnedSlots: Set<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Move $name to position", style = MaterialTheme.typography.titleMedium) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Orbit.spacing.sm),
            ) {
                (1..count).forEach { slot ->
                    val enabled = slot != current && slot !in pinnedSlots
                    val label = when {
                        slot == current -> "$slot (now)"
                        slot in pinnedSlots -> "$slot (pinned)"
                        else -> "$slot"
                    }
                    Box(
                        Modifier
                            .heightIn(min = Orbit.sizing.touchTarget)
                            .clip(CircleShape)
                            .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .then(if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = "Move to position $slot") { onPick(slot) } else Modifier)
                            .padding(horizontal = Orbit.spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Cancel") } },
    )
}
