package com.smartdialer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The order the user set by dragging bubbles. It is temporary: it applies to the ranked list (before the hard
 * rules) until the next successful ranking refresh lands, which clears it. Moves are never sent to the LLM.
 */
interface ManualArrangement {
    /** Contact ids in the order they were displayed, or null when there is no manual arrangement. */
    val order: StateFlow<List<String>?>
    fun set(order: List<String>?)
    fun clear() = set(null)
}

class InMemoryManualArrangement : ManualArrangement {
    private val state = MutableStateFlow<List<String>?>(null)
    override val order: StateFlow<List<String>?> = state.asStateFlow()
    override fun set(order: List<String>?) {
        state.value = order?.takeIf { it.isNotEmpty() }
    }
}

/** Persisted locally, so the arrangement survives a restart until the next ranking refresh. */
class PrefsManualArrangement(context: Context) : ManualArrangement {
    private val prefs = context.getSharedPreferences("orbit_manual", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val order: StateFlow<List<String>?> = state.asStateFlow()

    private fun read(): List<String>? =
        prefs.getString("order", null)?.split('\n')?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }

    override fun set(order: List<String>?) {
        val value = order?.takeIf { it.isNotEmpty() }
        if (value == null) prefs.edit().remove("order").apply() else prefs.edit().putString("order", value.joinToString("\n")).apply()
        state.value = value
    }
}
