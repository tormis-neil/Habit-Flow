package com.habitflow.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import com.habitflow.app.data.local.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Day 4 — Firestore data source for Habit Logs.
 *
 * Responsible for:
 *  - PUSH: upsert a local HabitLogEntity to Firestore under users/{uid}/habit_logs/{uuid}
 *  - PULL: snapshot listener that streams remote log changes for the signed-in user
 *  - DELETE: write a tombstone (isDeleted = true) on a log
 *
 * Firestore document shape (matches MCO2_REQUIREMENTS §4):
 *   habitUuid, dateCompleted, timestamp, isDeleted, updatedAt (server timestamp)
 *
 * Note: the Firestore log uses habitUuid (string) — NOT the local Room habitId (Long) —
 * because the uuid is the stable cross-device identifier.
 */
@Singleton
class FirestoreLogDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    // ─── Collection helpers ───────────────────────────────────────────────────

    private fun logsRef(uid: String) =
        firestore.collection("users").document(uid).collection("habit_logs")

    // ─── Push ─────────────────────────────────────────────────────────────────

    /**
     * Writes (or overwrites) a log document to Firestore.
     * Uses the log's [uuid] as the document ID.
     */
    suspend fun push(uid: String, log: HabitLogEntity, habitUuid: String) {
        logsRef(uid).document(log.uuid).set(log.toFirestoreMap(habitUuid)).await()
    }

    /**
     * Writes a tombstone for a deleted log.
     */
    suspend fun pushTombstone(uid: String, uuid: String) {
        logsRef(uid).document(uuid).update(
            mapOf(
                "isDeleted" to true,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ─── Pull ─────────────────────────────────────────────────────────────────

    /**
     * Real-time Flow of all log documents for the signed-in user.
     * Emits on every Firestore push update.
     */
    fun observeLogs(uid: String): Flow<List<RemoteLog>> =
        logsRef(uid).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(RemoteLog::class.java)?.copy(uuid = doc.id)
            }
        }

    /**
     * One-shot fetch of logs updated after [sinceMs].
     * Used during the PULL phase of HabitSyncWorker (Day 6).
     */
    suspend fun fetchUpdatedSince(uid: String, sinceMs: Long): List<RemoteLog> {
        val snapshot = logsRef(uid)
            .whereGreaterThan("updatedAtMs", sinceMs)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(RemoteLog::class.java)?.copy(uuid = doc.id)
        }
    }
}

// ─── Wire-format model ────────────────────────────────────────────────────────

/**
 * The shape of a habit_log document as it exists in Firestore.
 * All fields need defaults for Firestore's no-arg reflection constructor.
 */
data class RemoteLog(
    val uuid: String = "",
    /** The uuid of the parent habit — cross-device stable reference. */
    val habitUuid: String = "",
    /** Epoch day (same unit as HabitLogEntity.dateCompleted). */
    val dateCompleted: Long = 0L,
    /** Unix timestamp in ms of when the completion was recorded. */
    val timestamp: Long = 0L,
    val isDeleted: Boolean = false,
    @ServerTimestamp val updatedAt: Date? = null,
) {
    val updatedAtMs: Long get() = updatedAt?.time ?: 0L
}

/** Converts a [HabitLogEntity] to the Map Firestore expects. */
private fun HabitLogEntity.toFirestoreMap(habitUuid: String): Map<String, Any?> = mapOf(
    "habitUuid"    to habitUuid,
    "dateCompleted" to dateCompleted,
    "timestamp"    to timestamp,
    "isDeleted"    to isDeleted,
    "updatedAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
    "updatedAtMs"  to updatedAt,
)
