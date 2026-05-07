package com.habitflow.app.domain.repository

import com.habitflow.app.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUserId: String?
    /** @throws Exception on network error, wrong credentials, etc. */
    suspend fun signUp(email: String, password: String, username: String)
    /** @throws Exception on network error or wrong credentials. */
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    /** @throws Exception if the email is not registered or network fails. */
    suspend fun sendPasswordReset(email: String)
}
