package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.data.local.ThemePreferences
import com.habitflow.app.domain.model.UserProfile
import com.habitflow.app.domain.repository.AuthRepository
import com.habitflow.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        viewModelScope.launch {
            _userProfile.value = userRepository.getCachedProfile()
        }
    }

    fun setDarkMode(enabled: Boolean) = themePreferences.setDarkMode(enabled)

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _userProfile.value = null
        }
    }
}
