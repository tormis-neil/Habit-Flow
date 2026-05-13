package com.habitflow.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import com.habitflow.app.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Day 4 — Firestore data source for Habits.
 *
 * Responsible for:
 *  - PUSH: upsert a local HabitEntity to Firestore under users/{uid}/habits/{uuid}
 *  - PULL: snapshot listener that streams remote habit changes for the signed-in user
 *  - DELETE: write a tombstone (isDeleted = true) so other devices learn about the deletion
 *
 * This class knows nothing about Room — it only speaks Firestore.
 * The Repository (Day 5) coordinates between this source and Room.
 *
 * Firestore document shape (matches MCO2_REQUIREMENTS §4):
 *   title, description, frequency, startDate, isEnabled, color,
 *   reminderTime, reminderEnabled, isDeleted, updatedAt (server timestamp)
 */
@Singleton
class FirestoreHabitDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    // ─── Collection helpers ───────────────────────────────────────────────────

    /** Returns the habits sub-collection for a given user. */
    private fun habitsRef(uid: String) =
        firestore.collection("users").document(uid).collection("habits")

    // ─── Push (local → cloud) ─────────────────────────────────────────────────

    /**
     * Writes (or overwrites) a habit document to Firestore.
     * Uses the habit's [uuid] as the document ID for stable cross-device identity.
     * [updatedAt] is set to a server timestamp so all devices agree on the time.
     */
    suspend fun push(uid: String, habit: HabitEntity) {
        habitsRef(uid).document(habit.uuid).set(habit.toFirestoreMap()).await()
    }

    /**
     * Writes a tombstone for a deleted habit. Instead of removing the document,
     * we set isDeleted=true so other devices can learn about the deletion on next sync.
     */
    suspend fun pushTombstone(uid: String, uuid: String) {
        habitsRef(uid).document(uuid).update(
            mapOf(
                "isDeleted" to true,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ─── Pull (cloud → local) ─────────────────────────────────────────────────

    /**
     * Returns a real-time Flow of ALL habit documents for the signed-in user,
     * including tombstoned (deleted) ones so the sync worker can apply deletions.
     * This Flow emits whenever Firestore pushes a change — no polling needed.
     */
    fun observeHabits(uid: String): Flow<List<RemoteHabit>> =
        habitsRef(uid).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(RemoteHabit::class.java)?.copy(uuid = doc.id)
            }
        }

    /**
     * One-shot fetch of all habits updated after [sinceMs].
     * Used during the PULL phase of HabitSyncWorker (Day 6).
     */
    suspend fun fetchUpdatedSince(uid: String, sinceMs: Long): List<RemoteHabit> {
        val snapshot = habitsRef(uid)
            .whereGreaterThan("updatedAtMs", sinceMs)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(RemoteHabit::class.java)?.copy(uuid = doc.id)
        }
    }
}

// ─── Wire-format model ────────────────────────────────────────────────────────

/**
 * The shape of a habit document as it exists in Firestore.
 * Firestore deserializes documents into this data class automatically.
 * Every field must have a default value for Firestore's no-arg constructor.
 */
data class RemoteHabit(
    val uuid: String = "",
    val title: String = "",
    val description: String = "",
    val frequency: String = "DAILY",
    val startDate: Long = LocalDate.now().toEpochDay(),   // epoch day (not epoch ms)
    val isEnabled: Boolean = true,
    val color: String = "#6750A4",
    val reminderTime: String? = null,
    val reminderEnabled: Boolean = false,
    val isDeleted: Boolean = false,
    // Server timestamp stored as a Date by Firestore; we expose it as ms for Room
    @ServerTimestamp val updatedAt: Date? = null,
) {
    /** Convenience: updatedAt as epoch milliseconds (safe even if null). */
    val updatedAtMs: Long get() = updatedAt?.time ?: 0L
}

/** Converts a [HabitEntity] to the Map Firestore expects. */
private fun HabitEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title"           to title,
    "description"     to description,
    "frequency"       to frequency,
    "startDate"       to startDate,
    "isEnabled"       to isEnabled,
    "color"           to color,
    "reminderTime"    to reminderTime,
    "reminderEnabled" to reminderEnabled,
    "isDeleted"       to isDeleted,
    // updatedAt is always written as a server timestamp so all devices agree
    "updatedAt"       to com.google.firebase.firestore.FieldValue.serverTimestamp(),
    // Also store the local ms value for the "updatedSince" query on pull
    "updatedAtMs"     to updatedAt,
)
