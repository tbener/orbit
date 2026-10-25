package com.smartdialer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the home screen arranges the bubbles. Slots are numbered by rank (1, 2, 3...) in every layout. */
enum class LayoutMode(val label: String) {
    /** Rows of 3 equal bubbles, #1 top-left, reading left to right and top to bottom. The default. */
    Grid3("Grid 3"),

    /** Rows of 4 equal bubbles, same order. */
    Grid4("Grid 4"),

    /** #1 largest in the middle with #2 and #3 beside it, then rows of 4 smaller bubbles. */
    Spotlight("Spotlight"),
}

/** Local, persisted display preferences. */
class UiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("orbit_ui", Context.MODE_PRIVATE)
    private val _layout = MutableStateFlow(read())
    val layoutMode: StateFlow<LayoutMode> = _layout.asStateFlow()

    private fun read(): LayoutMode =
        LayoutMode.entries.firstOrNull { it.name == prefs.getString("layout", null) } ?: LayoutMode.Grid3

    fun setLayoutMode(mode: LayoutMode) {
        prefs.edit().putString("layout", mode.name).apply()
        _layout.value = mode
    }
}
