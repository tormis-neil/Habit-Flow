package com.habitflow.app.data.repository

import com.habitflow.app.data.remote.FirebaseAuthDataSource
import com.habitflow.app.domain.model.AuthState
import com.habitflow.app.domain.repository.AuthRepository
import com.habitflow.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val userRepository: UserRepository,
) : AuthRepository {

    // Singleton-scoped scope: lives as long as the app process.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Seed the initial state synchronously so the NavGraph never shows the
    // wrong start destination — no async gap before the first Firebase callback.
    private val _authState = MutableStateFlow<AuthState>(
        authDataSource.currentUser?.let { AuthState.Authenticated(it.uid) }
            ?: AuthState.Unauthenticated,
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUserId: String? get() = authDataSource.currentUser?.uid

    init {
        // Keep _authState in sync with Firebase whenever the token changes
        // (sign-in, sign-out, token revocation, etc.).
        scope.launch {
            authDataSource.authStateFlow.collect { user ->
                _authState.value = if (user != null) AuthState.Authenticated(user.uid)
                                   else AuthState.Unauthenticated
            }
        }
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<Unit> =
        runCatching {
            val user = authDataSource.createUser(email, password)
            userRepository.createProfile(user.uid, email, username).getOrThrow()
        }

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { authDataSource.signIn(email, password) }

    override suspend fun signOut() {
        authDataSource.signOut()
        userRepository.clearProfile()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching { authDataSource.sendPasswordReset(email) }
}
