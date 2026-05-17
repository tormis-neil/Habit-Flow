package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.data.local.ThemePreferences
import com.habitflow.app.data.local.UserPreferences
import com.habitflow.app.data.notification.ReminderScheduler
import com.habitflow.app.domain.model.UserProfile
import com.habitflow.app.domain.repository.AuthRepository
import com.habitflow.app.domain.repository.HabitRepository
import com.habitflow.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.habitflow.app.domain.model.AuthState
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val habitRepository: HabitRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode

    val dailyRemindersEnabled: StateFlow<Boolean> = userPreferences.dailyRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        // Re-fetch the profile every time the user becomes authenticated.
        // This covers: first sign-in, sign-out then sign-in again, token refresh.
        viewModelScope.launch {
            authRepository.authState
                .filterIsInstance<AuthState.Authenticated>()
                .collectLatest {
                    _userProfile.value = userRepository.getCachedProfile()
                }
        }
    }

    fun setDarkMode(enabled: Boolean) = themePreferences.setDarkMode(enabled)

    fun setDailyRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDailyRemindersEnabled(enabled)
            val allHabits = habitRepository.habits.value
            allHabits.forEach { habit ->
                if (enabled && habit.reminderEnabled) {
                    reminderScheduler.scheduleReminder(habit)
                } else {
                    reminderScheduler.cancelReminder(habit.id)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _userProfile.value = null
        }
    }
}
