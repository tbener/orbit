package com.smartdialer.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/** A short message for a snackbar, optionally with an undo action. */
class UserMessage(val text: String, val undo: (() -> Unit)? = null)

/** Shows each message from [messages] in [host], and runs its undo action if the user taps Undo. */
@Composable
fun CollectUserMessages(messages: Flow<UserMessage>, host: SnackbarHostState) {
    LaunchedEffect(messages) {
        messages.collect { message ->
            val result = host.showSnackbar(
                message = message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
        }
    }
}
