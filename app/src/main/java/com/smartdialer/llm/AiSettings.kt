package com.smartdialer.llm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AiSettingsState(
    /** The "AI suggestions" switch. Off means the fallback ranker only, and no network calls at all. */
    val enabled: Boolean = true,
    /** True once the user has read and accepted what is sent to the Anthropic API. */
    val disclosureAcknowledged: Boolean = false,
    /** When the last refresh was tried, and when one last worked. */
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    /** Set when the most recent attempt failed; cleared by the next success. */
    val lastFailureAt: Long? = null,
    /** A plain sentence about how the last refresh went. */
    val lastStatus: String? = null,
) {
    /** Nothing is ever sent unless the switch is on and the disclosure was acknowledged. */
    val canCallLlm: Boolean get() = enabled && disclosureAcknowledged
}

interface AiSettings {
    val state: StateFlow<AiSettingsState>
    fun setEnabled(enabled: Boolean)
    fun acknowledgeDisclosure()
    fun recordSuccess(at: Long)
    fun recordFailure(at: Long, message: String)
}

class InMemoryAiSettings(initial: AiSettingsState = AiSettingsState(disclosureAcknowledged = true)) : AiSettings {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<AiSettingsState> = _state.asStateFlow()

    override fun setEnabled(enabled: Boolean) = _state.update { it.copy(enabled = enabled) }
    override fun acknowledgeDisclosure() = _state.update { it.copy(disclosureAcknowledged = true) }
    override fun recordSuccess(at: Long) =
        _state.update { it.copy(lastAttemptAt = at, lastSuccessAt = at, lastFailureAt = null, lastStatus = "Updated") }

    override fun recordFailure(at: Long, message: String) =
        _state.update { it.copy(lastAttemptAt = at, lastFailureAt = at, lastStatus = message) }
}

/** Keeps the settings in SharedPreferences. */
class SharedPrefsAiSettings(context: Context) : AiSettings {
    private val prefs = context.getSharedPreferences("orbit_ai", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    override val state: StateFlow<AiSettingsState> = _state.asStateFlow()

    private fun read() = AiSettingsState(
        enabled = prefs.getBoolean(ENABLED, true),
        disclosureAcknowledged = prefs.getBoolean(ACKNOWLEDGED, false),
        lastAttemptAt = prefs.getLong(LAST_ATTEMPT, 0L).takeIf { it > 0 },
        lastSuccessAt = prefs.getLong(LAST_SUCCESS, 0L).takeIf { it > 0 },
        lastFailureAt = prefs.getLong(LAST_FAILURE, 0L).takeIf { it > 0 },
        lastStatus = prefs.getString(LAST_STATUS, null),
    )

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _state.value = read()
    }

    override fun setEnabled(enabled: Boolean) = edit { putBoolean(ENABLED, enabled) }
    override fun acknowledgeDisclosure() = edit { putBoolean(ACKNOWLEDGED, true) }
    override fun recordSuccess(at: Long) = edit {
        putLong(LAST_ATTEMPT, at).putLong(LAST_SUCCESS, at).remove(LAST_FAILURE).putString(LAST_STATUS, "Updated")
    }

    override fun recordFailure(at: Long, message: String) = edit {
        putLong(LAST_ATTEMPT, at).putLong(LAST_FAILURE, at).putString(LAST_STATUS, message)
    }

    private companion object {
        const val ENABLED = "enabled"
        const val ACKNOWLEDGED = "disclosure_acknowledged"
        const val LAST_ATTEMPT = "last_attempt"
        const val LAST_SUCCESS = "last_success"
        const val LAST_FAILURE = "last_failure"
        const val LAST_STATUS = "last_status"
    }
}
