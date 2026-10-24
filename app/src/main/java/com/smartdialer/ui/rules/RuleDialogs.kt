package com.smartdialer.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smartdialer.data.Contact
import com.smartdialer.data.MAX_SUGGESTIONS
import com.smartdialer.rules.ContactRef
import com.smartdialer.rules.FreeTextRule
import com.smartdialer.rules.TypedRule
import com.smartdialer.rules.TypedRuleParams
import com.smartdialer.rules.TypedRuleType
import com.smartdialer.ui.ContactAvatar
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.bubbleColorsFor

private const val MAX_SLOT = MAX_SUGGESTIONS

@Composable
fun EditFreeTextDialog(
    rule: FreeTextRule,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(rule.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
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
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Cancel") }
        },
    )
}

/**
 * Form for adding ([initial] = null) or editing a typed rule: pick a type, a contact and, for pins, a slot.
 * With [fixedContact] the contact is set and cannot be changed (used on a contact's Details screen).
 * [initialType] preselects the type when adding.
 */
@Composable
fun TypedRuleDialog(
    initial: TypedRule?,
    contacts: List<Contact>?,
    onDismiss: () -> Unit,
    onSave: (TypedRuleParams) -> Unit,
    fixedContact: ContactRef? = null,
    initialType: TypedRuleType? = null,
) {
    var type by remember { mutableStateOf(initial?.params?.type ?: initialType ?: TypedRuleType.PIN_TO_SLOT) }
    var contact by remember { mutableStateOf(fixedContact ?: initial?.params?.contact) }
    var slotText by remember {
        mutableStateOf((initial?.params as? TypedRuleParams.PinToSlot)?.slot?.toString() ?: "1")
    }
    var picking by remember { mutableStateOf(false) }

    val slot = slotText.toIntOrNull()
    val slotValid = slot != null && slot in 1..MAX_SLOT
    val canSave = contact != null && (type != TypedRuleType.PIN_TO_SLOT || slotValid)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (initial == null) "Add typed rule" else "Edit typed rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm)) {
                    FilterChip(
                        selected = type == TypedRuleType.PIN_TO_SLOT,
                        onClick = { type = TypedRuleType.PIN_TO_SLOT },
                        label = { Text("Pin to slot") },
                    )
                    FilterChip(
                        selected = type == TypedRuleType.NEVER_SHOW,
                        onClick = { type = TypedRuleType.NEVER_SHOW },
                        label = { Text("Never show") },
                    )
                }
                if (fixedContact == null) {
                    OutlinedButton(
                        onClick = { picking = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Orbit.sizing.touchTarget),
                    ) {
                        Text(contact?.displayName ?: "Choose contact…")
                    }
                } else {
                    Text("For ${fixedContact.displayName}", style = MaterialTheme.typography.bodyLarge)
                }
                if (type == TypedRuleType.PIN_TO_SLOT) {
                    OutlinedTextField(
                        value = slotText,
                        onValueChange = { value -> slotText = value.filter { it.isDigit() }.take(2) },
                        label = { Text("Slot (1 = top, up to $MAX_SLOT)") },
                        singleLine = true,
                        isError = !slotValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ref = contact ?: return@TextButton
                    onSave(
                        when (type) {
                            TypedRuleType.PIN_TO_SLOT -> TypedRuleParams.PinToSlot(ref, slot ?: 1)
                            TypedRuleType.NEVER_SHOW -> TypedRuleParams.NeverShow(ref)
                        },
                    )
                },
                enabled = canSave,
                modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Cancel") }
        },
    )

    if (picking) {
        ContactPickerDialog(
            contacts = contacts,
            onDismiss = { picking = false },
            onPick = {
                contact = ContactRef(lookupKey = it.id, displayName = it.name)
                picking = false
            },
        )
    }
}

@Composable
private fun ContactPickerDialog(
    contacts: List<Contact>?,
    onDismiss: () -> Unit,
    onPick: (Contact) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = contacts.orEmpty().filter { it.name.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Choose contact") },
        text = {
            if (contacts == null) {
                Text("Contacts are not available. Allow contacts access on the home screen first.")
            } else {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = Orbit.sizing.popupWidth).padding(top = Orbit.spacing.sm)) {
                        items(shown, key = { it.id }) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Orbit.sizing.touchTarget + Orbit.spacing.sm)
                                    .clickable { onPick(contact) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContactAvatar(contact, diameter = Orbit.sizing.avatarMedium - Orbit.spacing.sm, initialsSp = 15, colors = bubbleColorsFor(contact.id))
                                Spacer(Modifier.width(Orbit.spacing.md))
                                Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        if (shown.isEmpty()) {
                            item { Text("No contacts found.", modifier = Modifier.padding(vertical = Orbit.spacing.md)) }
                        }
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

@Composable
fun ConfirmDeleteDialog(
    summary: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Delete this rule?") },
        text = { Text(summary) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Cancel") }
        },
    )
}
