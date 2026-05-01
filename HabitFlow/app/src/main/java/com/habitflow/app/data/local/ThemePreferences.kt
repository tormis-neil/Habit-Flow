package com.habitflow.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists theme preferences using SharedPreferences.
 * Exposes a reactive [StateFlow] for the dark mode setting so
 * [com.habitflow.app.presentation.ui.MainActivity] can observe it.
 */
class ThemePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))

    /** `true` when the user has explicitly enabled dark mode. */
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _isDarkMode.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "habitflow_theme_prefs"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
