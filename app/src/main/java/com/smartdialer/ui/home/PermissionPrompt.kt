package com.smartdialer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Which of the two runtime permissions the app has, and whether asking again is pointless. */
data class AccessState(
    val contacts: Boolean,
    val callLog: Boolean,
    /** True once the system dialog will no longer be shown, so only app settings can grant access. */
    val needsSettings: Boolean = false,
) {
    val all: Boolean get() = contacts && callLog
}

@Composable
fun PermissionPrompt(
    access: AccessState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val missing = buildList {
        if (!access.contacts) add("contacts")
        if (!access.callLog) add("call history")
    }.joinToString(" and ")

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Contacts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Orbit needs your $missing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = if (access.needsSettings) {
                "Access was turned off. Open Orbit's app settings and allow Contacts and Phone (call logs)."
            } else {
                "Orbit reads your contacts and call history to work out who you should call next."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = if (access.needsSettings) onOpenSettings else onRequest,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(if (access.needsSettings) "Open settings" else "Allow access")
        }
        Spacer(Modifier.width(1.dp))
    }
}
