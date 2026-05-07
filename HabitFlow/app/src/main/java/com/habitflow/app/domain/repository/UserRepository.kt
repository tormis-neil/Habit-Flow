package com.habitflow.app.domain.repository

import com.habitflow.app.domain.model.UserProfile

interface UserRepository {
    suspend fun createProfile(uid: String, email: String, username: String): Result<Unit>
    suspend fun getCachedProfile(): UserProfile?
    suspend fun clearProfile()
}
