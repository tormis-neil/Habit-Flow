package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.habitflow.app.data.local.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the Settings screen.
 * Delegates dark-mode state to [ThemePreferences] which persists
 * the choice and broadcasts it to the theme layer via [StateFlow].
 */
class SettingsViewModel(
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    /** Observable dark-mode toggle state. */
    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode

    /** Persists and broadcasts the user's dark-mode preference. */
    fun setDarkMode(enabled: Boolean) {
        themePreferences.setDarkMode(enabled)
    }
}
