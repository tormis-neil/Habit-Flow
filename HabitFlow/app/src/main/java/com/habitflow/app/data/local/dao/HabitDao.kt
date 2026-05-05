package com.habitflow.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.habitflow.app.data.local.entity.HabitEntity
import com.habitflow.app.data.local.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * FEATURE B — Local Data Persistence (Room)
 *
 * HabitDao (Data Access Object) is the layer that directly talks to the database.
 * It contains all SQL queries the app needs, written as annotated Kotlin functions.
 *
 * Think of it like a waiter in a restaurant:
 *  - The app (ViewModel) places an order → the DAO fetches or writes data → returns the result.
 * The app never writes raw SQL — Room generates the database code from these annotations.
 */
@Dao
interface HabitDao {

    // ─── Habit Operations ────────────────────────────────────────────────────

    /**
     * Saves a habit to the database.
     * `@Upsert` = INSERT if new, UPDATE if it already exists (based on primary key).
     * Returns the database row ID of the saved habit.
     */
    @Upsert
    suspend fun upsertHabit(habit: HabitEntity): Long

    /** Returns a live stream of ALL habits, ordered by ID. The UI auto-updates when data changes. */
    @Query("SELECT * FROM habits WHERE is_deleted = 0 ORDER BY id ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    /** Returns only habits that are currently enabled (not paused). */
    @Query("SELECT * FROM habits WHERE is_enabled = 1 AND is_deleted = 0 ORDER BY id ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    /** Fetches a single habit by its ID — used when opening the detail or edit screen. */
    @Query("SELECT * FROM habits WHERE id = :id AND is_deleted = 0")
    suspend fun getHabitById(id: Long): HabitEntity?

    /** Soft-deletes a habit row by marking it as deleted. */
    @Query("UPDATE habits SET is_deleted = 1, is_synced = 0, updated_at = :timestamp WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long, timestamp: Long = System.currentTimeMillis())

    /** Flips the enabled/disabled flag on a habit, marking it dirty. */
    @Query("UPDATE habits SET is_enabled = :enabled, is_synced = 0, updated_at = :timestamp WHERE id = :habitId")
    suspend fun setEnabled(habitId: Long, enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    // ─── Log Operations ──────────────────────────────────────────────────────

    /**
     * Writes a new completion log entry.
     * `OnConflictStrategy.IGNORE` means: if a log already exists for this habit+date,
     * do nothing — preventing duplicate completions on the same day.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity)

    /** Soft-removes a completion log — used when the user un-checks a habit. */
    @Query("UPDATE habit_logs SET is_deleted = 1, is_synced = 0, updated_at = :timestamp WHERE habit_id = :habitId AND date_completed = :dateCompleted")
    suspend fun deleteLog(habitId: Long, dateCompleted: Long, timestamp: Long = System.currentTimeMillis())

    /** Restores a completion log that was previously soft-deleted. */
    @Query("UPDATE habit_logs SET is_deleted = 0, is_synced = 0, updated_at = :timestamp WHERE logId = :logId")
    suspend fun restoreLog(logId: Long, timestamp: Long = System.currentTimeMillis())

    /** Returns a live stream of all completion logs for one habit, newest first. */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId AND is_deleted = 0 ORDER BY date_completed DESC")
    fun getLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>>

    /** Returns a live stream of every log across all habits — used to calculate streaks globally. */
    @Query("SELECT * FROM habit_logs WHERE is_deleted = 0 ORDER BY date_completed DESC")
    fun getAllLogs(): Flow<List<HabitLogEntity>>

    /** Checks whether a specific habit has a log for a specific date — returns null if not done. */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId AND date_completed = :dateCompleted LIMIT 1")
    suspend fun getLogForDate(habitId: Long, dateCompleted: Long): HabitLogEntity?

    /**
     * Atomically TOGGLES the completion state for a habit on a given date.
     * `@Transaction` guarantees the check + insert/delete happen as a single operation,
     * preventing bugs if two actions happen at the same time.
     *
     * Logic:
     *  - If a log already exists → delete it (un-complete)
     *  - If no log exists → insert a new one (complete)
     * Returns: true = now completed, false = now un-completed
     */
    @Transaction
    suspend fun toggleLog(habitId: Long, dateCompleted: Long, timestamp: Long): Boolean {
        val existing = getLogForDate(habitId, dateCompleted)
        return if (existing != null && !existing.isDeleted) {
            deleteLog(habitId, dateCompleted, timestamp)
            false  // Was completed, now un-completed
        } else if (existing != null && existing.isDeleted) {
            restoreLog(existing.logId, timestamp)
            true   // Restored
        } else {
            insertLog(
                HabitLogEntity(
                    habitId = habitId,
                    dateCompleted = dateCompleted,
                    timestamp = timestamp,
                )
            )
            true   // Was not completed, now completed
        }
    }

    // ─── Sync Operations ─────────────────────────────────────────────────────

    @Query("SELECT * FROM habits WHERE is_synced = 0 AND user_id = :userId")
    suspend fun getDirtyHabits(userId: String): List<HabitEntity>

    @Query("SELECT * FROM habit_logs WHERE is_synced = 0 AND user_id = :userId")
    suspend fun getDirtyLogs(userId: String): List<HabitLogEntity>

    @Query("SELECT * FROM habits WHERE uuid = :uuid LIMIT 1")
    suspend fun getHabitByUuid(uuid: String): HabitEntity?

    @Query("SELECT * FROM habit_logs WHERE uuid = :uuid LIMIT 1")
    suspend fun getLogByUuid(uuid: String): HabitLogEntity?

    @Query("UPDATE habits SET is_synced = 1 WHERE uuid = :uuid")
    suspend fun markHabitSynced(uuid: String)

    @Query("UPDATE habit_logs SET is_synced = 1 WHERE uuid = :uuid")
    suspend fun markLogSynced(uuid: String)
}
