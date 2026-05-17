package com.habitflow.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.habitflow.app.data.local.dao.UserDao
import com.habitflow.app.data.local.entity.UserProfileEntity
import com.habitflow.app.domain.model.UserProfile
import com.habitflow.app.domain.repository.UserRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
) : UserRepository {

    override suspend fun createProfile(uid: String, email: String, username: String) {
        val usernameLower = username.lowercase()
        val now = System.currentTimeMillis()

        // Atomically reserve the username and write the profile doc (AUTH-4).
        // The transaction throws if the username is already taken.
        firestore.runTransaction { tx ->
            val usernameRef = firestore.collection("usernames").document(usernameLower)
            if (tx.get(usernameRef).exists()) {
                throw IllegalStateException("Username \"$username\" is already taken")
            }
            tx.set(usernameRef, mapOf("uid" to uid))
            tx.set(
                firestore.collection("users").document(uid),
                mapOf(
                    "email" to email,
                    "username" to username,
                    "usernameLower" to usernameLower,
                    "createdAt" to now,
                ),
            )
        }.await()

        // Cache profile locally so SettingsScreen can display the username offline.
        userDao.insertProfile(
            UserProfileEntity(uid = uid, email = email, username = username, createdAt = now),
        )
    }

    override suspend fun getCachedProfile(): UserProfile? =
        userDao.getProfile()?.let {
            UserProfile(uid = it.uid, email = it.email, username = it.username, createdAt = it.createdAt)
        }

    /**
     * Fetches the user profile from Firestore and writes it to the local Room cache.
     * Called after every successful sign-in so Settings always shows the correct account.
     * Silently ignores network failures — the cached value from the last successful fetch is used.
     */
    override suspend fun fetchAndCacheProfile(uid: String) {
        try {
            val doc = firestore.collection("users").document(uid).get().await()
            val email    = doc.getString("email")    ?: return
            val username = doc.getString("username") ?: return
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            userDao.insertProfile(
                UserProfileEntity(uid = uid, email = email, username = username, createdAt = createdAt)
            )
        } catch (_: Exception) {
            // Network unavailable — keep whatever is already cached in Room
        }
    }

    override suspend fun clearProfile() = userDao.deleteProfile()
}
