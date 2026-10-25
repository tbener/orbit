package com.smartdialer.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.smartdialer.ui.theme.glass
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.selection.toggleable
import com.smartdialer.data.LayoutMode
import com.smartdialer.llm.RefreshScheduler
import com.smartdialer.ui.components.InfoDialog
import com.smartdialer.ui.rules.GlassPanel
import com.smartdialer.ui.rules.RuleKind
import com.smartdialer.ui.rules.ScreenHeader
import com.smartdialer.ui.rules.SectionHeader
import com.smartdialer.ui.rules.SolidButton
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.OrbitBackground

class SettingsActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onAcceptDisclosure: () -> Unit,
    val onRefreshNow: () -> Unit,
    val onOpenInspector: () -> Unit,
    val onSetLayout: (LayoutMode) -> Unit,
    val onSetWhatsApp: (Boolean) -> Unit,
    val onOpenNotificationAccess: () -> Unit,
    val onDeleteWhatsApp: () -> Unit,
    val onBack: () -> Unit,
)

/** What leaves the phone. Shown once before the first call and any time from Settings. */
internal const val DISCLOSURE_BODY =
    "To rank your contacts and write the short reasons, Orbit sends this to the Anthropic API:\n\n" +
        "• contact names\n" +
        "• signals from your call history: how many days since you last spoke, missed calls you haven't " +
        "returned, how many calls\n" +
        "\u2022 if you turn on WhatsApp signals: how long ago a contact messaged you and how many times " +
        "(never the message text)\n" +
        "• the instructions you write in Orbit\n\n" +
        "Phone numbers, email addresses, photos and anything else on your contacts are never sent. You can see " +
        "exactly what was sent under Settings > What the LLM sees, and turn AI suggestions off at any time; " +
        "then Orbit uses a simple built-in ranking and makes no network calls."

/** What the WhatsApp feature reads. Shown from Settings and in the one-time prompt. */
internal const val WHATSAPP_BODY =
    "When Orbit has notification access, it notices WhatsApp (and WhatsApp Business) message notifications and " +
        "remembers only who it was from and when: a contact, a time and \"incoming\". It never reads, stores or " +
        "sends the message text, ignores group chats and every other app, and drops a sender it cannot match to " +
        "exactly one contact. It also remembers when you open a chat from Orbit's WhatsApp button.\n\n" +
        "This helps Orbit see who has been in touch. It is partial: replies you send inside WhatsApp are not seen. " +
        "Events are kept for 90 days, and you can delete them any time in Settings."

/** The one-time explanation. Nothing is sent until the user accepts it. */
@Composable
fun AiDisclosureDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Use AI suggestions?", style = MaterialTheme.typography.titleMedium) },
        text = { Text(DISCLOSURE_BODY, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onAccept, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) {
                Text("Use AI suggestions", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Not now") }
        },
    )
}

@Composable
fun SettingsScreen(state: SettingsUiState, nowMillis: Long, actions: SettingsActions) {
    var showDisclosure by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showWhatsAppInfo by remember { mutableStateOf(false) }
    var showLayoutInfo by remember { mutableStateOf(false) }

    OrbitBackground {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScreenHeader(title = "Settings", onBack = actions.onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Orbit.spacing.lg, end = Orbit.spacing.lg, bottom = Orbit.spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Orbit.spacing.md),
                ) {
                    item {
                        SectionHeader(
                            kind = RuleKind.Soft,
                            icon = Icons.Filled.GridView,
                            title = "Bubble layout",
                            subtitle = "How the bubbles are arranged.",
                            onInfo = { showLayoutInfo = true },
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Orbit.spacing.sm)) {
                            LayoutMode.entries.forEach { mode ->
                                LayoutChoice(mode, state.layoutMode == mode, { actions.onSetLayout(mode) }, Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        SectionHeader(
                            kind = RuleKind.Soft,
                            icon = Icons.Filled.AutoAwesome,
                            title = "AI suggestions",
                            subtitle = "Ranks your contacts and explains why.",
                            onInfo = { showDisclosure = true },
                        )
                    }
                    item {
                        GlassPanel {
                            val on = state.enabled && state.acknowledged
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Orbit.sizing.touchTarget)
                                    .toggleable(
                                        value = on,
                                        role = Role.Switch,
                                        onValueChange = { wantOn ->
                                            if (wantOn && !state.acknowledged) showDisclosure = true else actions.onSetEnabled(wantOn)
                                        },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Use AI suggestions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                                    Text(
                                        if (on) "On. Names, call-history signals and your instructions go to the Anthropic API."
                                        else "Off. Orbit uses a simple built-in ranking and sends nothing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = on, onCheckedChange = null)
                            }
                        }
                    }
                    if (state.apiKeyMissing) {
                        item {
                            Text(
                                "No API key is set. Add LLM_API_KEY to local.properties and rebuild to turn AI suggestions on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Orbit.colors.alertText,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    item {
                        GlassPanel {
                            Text("Last update", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                text = state.lastSuccessAt?.let { "Updated ${relativeTime(nowMillis, it)}." } ?: "No update yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.failing) {
                                Text(
                                    text = "The last try ${state.lastAttemptAt?.let { relativeTime(nowMillis, it) } ?: ""} failed: ${state.lastStatus ?: "unknown error"}. " +
                                        "Orbit keeps showing the last good result and will try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Orbit.colors.alertText,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = "Today: ${state.refreshCountToday} of ${state.refreshCap} refreshes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Scheduled: ${RefreshScheduler.DESCRIPTION}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.promptVersion != null) {
                                Text(
                                    text = "Prompt ${state.promptVersion} · ${state.model}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        SolidButton(
                            label = if (state.refreshing) "Updating…" else "Refresh now",
                            icon = Icons.Filled.Refresh,
                            onClick = { if (!state.refreshing && state.enabled && state.acknowledged) actions.onRefreshNow() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        SolidButton(
                            label = "What the LLM sees",
                            icon = Icons.Filled.Visibility,
                            onClick = actions.onOpenInspector,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        SectionHeader(
                            kind = RuleKind.Soft,
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = "WhatsApp signals",
                            subtitle = "Who messaged you, and when. Never the text.",
                            onInfo = { showWhatsAppInfo = true },
                            modifier = Modifier.padding(top = Orbit.spacing.lg),
                        )
                    }
                    item {
                        GlassPanel {
                            val status = when {
                                !state.whatsAppEnabled -> "Off"
                                !state.whatsAppGranted -> "Needs permission"
                                else -> "Active"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Orbit.sizing.touchTarget)
                                    .toggleable(value = state.whatsAppEnabled, role = Role.Switch, onValueChange = actions.onSetWhatsApp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Use WhatsApp signals", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                                    Text(
                                        "Status: $status",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status == "Needs permission") Orbit.colors.alertText else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Switch(checked = state.whatsAppEnabled, onCheckedChange = null)
                            }
                            Text(
                                "${state.whatsAppEventCount} event${if (state.whatsAppEventCount == 1) "" else "s"} stored on this phone (kept 90 days).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.whatsAppEnabled && !state.whatsAppGranted) {
                        item {
                            Text(
                                "Notification access is off. Open the system screen and allow Orbit. If the switch is greyed out " +
                                    "(common for apps installed outside the Play Store), open Orbit's App info, tap the three dots " +
                                    "and choose \"Allow restricted settings\", then try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        SolidButton(
                            label = "Notification access settings",
                            icon = Icons.Filled.Notifications,
                            onClick = actions.onOpenNotificationAccess,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        SolidButton(
                            label = "Delete WhatsApp data",
                            icon = Icons.Filled.Delete,
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            "Advanced: the exact request that was sent, the raw answer and anything that was repaired or dropped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
            }
        }
    }

    if (showLayoutInfo) {
        InfoDialog(
            title = "Bubble layout",
            body = "Grid 3 (default) and Grid 4 put equal bubbles in rows from the left, #1 at the top-left. Spotlight puts #1 " +
                "large in the middle with #2 and #3 beside it. Slots are numbered by rank in every layout, so a pin to slot 2 " +
                "means the same thing in all of them.",
            onDismiss = { showLayoutInfo = false },
        )
    }

    if (showWhatsAppInfo) {
        AlertDialog(
            onDismissRequest = { showWhatsAppInfo = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("WhatsApp signals", style = MaterialTheme.typography.titleMedium) },
            text = { Text(WHATSAPP_BODY, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showWhatsAppInfo = false }, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("OK") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete WhatsApp data?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("This removes every stored WhatsApp event (sender and time). It does not touch WhatsApp itself.") },
            confirmButton = {
                TextButton(onClick = { actions.onDeleteWhatsApp(); confirmDelete = false }, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Cancel") }
            },
        )
    }

    if (showDisclosure) {
        AiDisclosureDialog(
            onAccept = { actions.onAcceptDisclosure(); showDisclosure = false },
            onDecline = { showDisclosure = false },
        )
    }
}

/** "just now", "5 minutes ago", "3 hours ago", "2 days ago". */
internal fun relativeTime(nowMillis: Long, thenMillis: Long): String {
    val minutes = ((nowMillis - thenMillis) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        minutes < 60 * 24 -> "${minutes / 60} ${if (minutes / 60 == 1L) "hour" else "hours"} ago"
        else -> "${minutes / (60 * 24)} ${if (minutes / (60 * 24) == 1L) "day" else "days"} ago"
    }
}

/** The one-time prompt after the update: what is read, what is not, and where to grant it. */
@Composable
fun WhatsAppPrompt(onEnable: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Use WhatsApp as a signal?", style = MaterialTheme.typography.titleMedium) },
        text = { Text(WHATSAPP_BODY, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onEnable, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) {
                Text("Enable", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget)) { Text("Not now") }
        },
    )
}

/** One selectable layout: a small preview and its name. */
@Composable
private fun LayoutChoice(mode: LayoutMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Orbit.colors.accent
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .glass(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.border(2.dp, accent, MaterialTheme.shapes.medium) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Orbit.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Orbit.spacing.xs),
    ) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val dot = if (selected) accent else ink
            fun circle(x: Float, y: Float, r: Float) = drawCircle(dot.copy(alpha = 0.85f), r, Offset(x * size.width, y * size.height))
            when (mode) {
                LayoutMode.Grid3 -> for (row in 0 until 3) for (col in 0 until 3) circle(0.2f + 0.3f * col, 0.17f + 0.33f * row, size.width * 0.12f)
                LayoutMode.Grid4 -> for (row in 0 until 3) for (col in 0 until 4) circle(0.14f + 0.24f * col, 0.17f + 0.33f * row, size.width * 0.09f)
                LayoutMode.Spotlight -> {
                    circle(0.5f, 0.2f, size.width * 0.17f)
                    circle(0.15f, 0.2f, size.width * 0.1f)
                    circle(0.85f, 0.2f, size.width * 0.1f)
                    for (row in 0 until 2) for (col in 0 until 4) circle(0.14f + 0.24f * col, 0.55f + 0.27f * row, size.width * 0.075f)
                }
            }
        }
        Text(
            mode.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (selected) FontWeight.SemiBold else null,
        )
    }
}
