package com.habitflow.app.domain.repository

import com.habitflow.app.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUserId: String?
    suspend fun signUp(email: String, password: String, username: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signOut()
    suspend fun sendPasswordReset(email: String): Result<Unit>
}
