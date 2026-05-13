package com.habitflow.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitflow.app.data.local.SyncPreferenceManager
import com.habitflow.app.data.local.dao.HabitDao
import com.habitflow.app.data.local.entity.HabitEntity
import com.habitflow.app.data.local.entity.HabitLogEntity
import com.habitflow.app.data.remote.FirestoreHabitDataSource
import com.habitflow.app.data.remote.FirestoreLogDataSource
import com.habitflow.app.data.remote.RemoteHabit
import com.habitflow.app.data.remote.RemoteLog
import com.habitflow.app.domain.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Day 6 — HabitSyncWorker
 *
 * This background worker is responsible for synchronizing data between the
 * local Room database and Cloud Firestore. It runs periodically and handles
 * both PULL and PUSH operations for the currently signed-in user.
 */
@HiltWorker
class HabitSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val habitDao: HabitDao,
    private val habitDataSource: FirestoreHabitDataSource,
    private val logDataSource: FirestoreLogDataSource,
    private val syncPrefs: SyncPreferenceManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uid = authRepository.currentUserId
        if (uid == null) {
            Log.d("HabitSyncWorker", "No user signed in. Skipping sync.")
            return@withContext Result.success()
        }

        try {
            val lastSyncTime = syncPrefs.getLastSyncTime()
            val syncStartMs = System.currentTimeMillis()

            Log.d("HabitSyncWorker", "Starting sync for user: $uid. Last sync: $lastSyncTime")

            // 1. PULL updates from Firestore to local Room
            pullHabits(uid, lastSyncTime)
            pullLogs(uid, lastSyncTime)

            // 2. PUSH local dirty changes to Firestore
            pushHabits(uid)
            pushLogs(uid)

            // 3. Update the last sync time upon full success
            syncPrefs.updateLastSyncTime(syncStartMs)
            
            Log.d("HabitSyncWorker", "Sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("HabitSyncWorker", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun pullHabits(uid: String, lastSyncTime: Long) {
        val remoteHabits = habitDataSource.fetchUpdatedSince(uid, lastSyncTime)
        for (remote in remoteHabits) {
            val existing = habitDao.getHabitByUuid(remote.uuid)
            
            val localEntity = HabitEntity(
                id = existing?.id ?: 0L, // Keep local ID if it exists, otherwise auto-generate
                title = remote.title,
                description = remote.description,
                frequency = remote.frequency,
                startDate = remote.startDate,
                isEnabled = remote.isEnabled,
                color = remote.color,
                uuid = remote.uuid,
                userId = uid,
                updatedAt = remote.updatedAtMs,
                isDeleted = remote.isDeleted,
                isSynced = true, // We just pulled it, so it's in sync
                reminderTime = remote.reminderTime,
                reminderEnabled = remote.reminderEnabled
            )
            
            habitDao.upsertHabit(localEntity)
        }
    }

    private suspend fun pullLogs(uid: String, lastSyncTime: Long) {
        val remoteLogs = logDataSource.fetchUpdatedSince(uid, lastSyncTime)
        for (remote in remoteLogs) {
            // Logs reference habits by local ID in Room, but remote uses uuid.
            // We need to resolve the local habit ID first.
            val habitEntity = habitDao.getHabitByUuid(remote.habitUuid)
            if (habitEntity == null) {
                // If we don't have the parent habit locally yet, we skip this log.
                // In a perfect system, habits are pulled before logs, but if there's
                // a race condition, the next sync will catch it.
                Log.w("HabitSyncWorker", "Skipping log ${remote.uuid}: parent habit ${remote.habitUuid} not found locally.")
                continue
            }

            val existing = habitDao.getLogByUuid(remote.uuid)
            
            val localLog = HabitLogEntity(
                logId = existing?.logId ?: 0L,
                habitId = habitEntity.id, // Use the resolved local habit ID
                dateCompleted = remote.dateCompleted,
                timestamp = remote.timestamp,
                uuid = remote.uuid,
                userId = uid,
                updatedAt = remote.updatedAtMs,
                isDeleted = remote.isDeleted,
                isSynced = true // We just pulled it, so it's in sync
            )
            
            habitDao.upsertLog(localLog)
        }
    }

    private suspend fun pushHabits(uid: String) {
        val dirtyHabits = habitDao.getDirtyHabits(uid)
        for (habit in dirtyHabits) {
            if (habit.isDeleted) {
                habitDataSource.pushTombstone(uid, habit.uuid)
            } else {
                habitDataSource.push(uid, habit)
            }
            habitDao.markHabitSynced(habit.uuid)
        }
    }

    private suspend fun pushLogs(uid: String) {
        val dirtyLogs = habitDao.getDirtyLogs(uid)
        for (log in dirtyLogs) {
            // To push a log, we need the parent habit's uuid (for Firestore)
            val habit = habitDao.getHabitByIdIncludeDeleted(log.habitId)
            if (habit != null) {
                if (log.isDeleted) {
                    logDataSource.pushTombstone(uid, log.uuid)
                } else {
                    logDataSource.push(uid, log, habit.uuid)
                }
                habitDao.markLogSynced(log.uuid)
            } else {
                Log.e("HabitSyncWorker", "Cannot push log ${log.uuid}: parent habit locally missing.")
            }
        }
    }
}
