package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import android.os.Bundle
import com.habitflow.app.data.local.ThemePreferences
import com.habitflow.app.domain.repository.HabitRepository

/**
 * FEATURE A — Architecture & Structure
 *
 * HabitFlowViewModelFactory is responsible for BUILDING every ViewModel in the app.
 *
 * Normally, Android creates ViewModels automatically — but our ViewModels need
 * extra inputs (like the repository and route arguments). This factory supplies
 * those dependencies when each ViewModel is first created.
 *
 * `AbstractSavedStateViewModelFactory` also ensures that route arguments
 * (e.g. which habit ID to show) survive screen rotations via SavedStateHandle.
 */
class HabitFlowViewModelFactory(
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null,
    private val repository: HabitRepository,           // Shared data source for all habits
    private val themePreferences: ThemePreferences? = null, // Only needed by SettingsViewModel
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle, // Carries route args (e.g. habitId from the URL)
    ): T = when {
        // Match the requested ViewModel class and hand it the right dependencies.
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(repository) as T

        modelClass.isAssignableFrom(ProgressViewModel::class.java) ->
            ProgressViewModel(repository) as T

        // Detail & Form ViewModels also receive `handle` so they know which habit to load.
        modelClass.isAssignableFrom(HabitDetailViewModel::class.java) ->
            HabitDetailViewModel(handle, repository) as T

        modelClass.isAssignableFrom(HabitFormViewModel::class.java) ->
            HabitFormViewModel(handle, repository) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(checkNotNull(themePreferences) { "ThemePreferences required for SettingsViewModel" }) as T

        // Safeguard: crash early if a new ViewModel is added but not registered here.
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
