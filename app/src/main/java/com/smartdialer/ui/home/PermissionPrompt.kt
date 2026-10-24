package com.smartdialer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import com.smartdialer.ui.theme.Orbit
import com.smartdialer.ui.theme.PillShape
import com.smartdialer.ui.theme.glassCircle

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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Orbit.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Orbit.sizing.avatarLarge)
                .glassCircle(strong = true, elevation = Orbit.elevation.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Contacts,
                contentDescription = null,
                tint = Orbit.colors.accent,
                modifier = Modifier.size(Orbit.sizing.emptyStateIcon),
            )
        }
        Spacer(Modifier.height(Orbit.spacing.lg))
        Text(
            text = "Orbit needs your $missing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Orbit.spacing.sm))
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
        Spacer(Modifier.height(Orbit.spacing.xl))
        Button(
            onClick = if (access.needsSettings) onOpenSettings else onRequest,
            shape = PillShape,
            modifier = Modifier.heightIn(min = Orbit.sizing.touchTarget),
        ) {
            Text(if (access.needsSettings) "Open settings" else "Allow access", style = MaterialTheme.typography.labelLarge)
        }
    }
}
