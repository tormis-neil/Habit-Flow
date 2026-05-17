package com.habitflow.app.presentation.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.domain.model.AuthState
import com.habitflow.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val passwordResetSent: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signUp(email: String, password: String, username: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.signUp(email.trim(), password, username.trim())
                _uiState.value = AuthUiState()
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = e.message ?: "Sign-up failed")
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!isNetworkAvailable()) {
            _uiState.value = AuthUiState(
                isOffline = true,
                error = "No internet connection. Please reconnect to sign in."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.signIn(email.trim(), password)
                _uiState.value = AuthUiState()
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = e.message ?: "Sign-in failed")
            }
        }
    }

    /** Returns true if the device currently has an active internet connection. */
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.sendPasswordReset(email.trim())
                _uiState.value = AuthUiState(passwordResetSent = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = e.message ?: "Reset failed")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, passwordResetSent = false)
    }
}
