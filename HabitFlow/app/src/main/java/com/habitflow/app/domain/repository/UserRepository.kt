package com.habitflow.app.domain.repository

import com.habitflow.app.domain.model.UserProfile

interface UserRepository {
    /** @throws Exception if the username is taken or network fails. */
    suspend fun createProfile(uid: String, email: String, username: String)
    /** Reads the user's profile from Firestore and saves it to the local Room cache. */
    suspend fun fetchAndCacheProfile(uid: String)
    suspend fun getCachedProfile(): UserProfile?
    suspend fun clearProfile()
}
