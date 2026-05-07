package com.habitflow.app.domain.model

sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val uid: String) : AuthState()
}
