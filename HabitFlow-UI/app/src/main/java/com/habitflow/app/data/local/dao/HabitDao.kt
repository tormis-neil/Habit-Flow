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
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    /** Returns only habits that are currently enabled (not paused). */
    @Query("SELECT * FROM habits WHERE is_enabled = 1 ORDER BY id ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    /** Fetches a single habit by its ID — used when opening the detail or edit screen. */
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Long): HabitEntity?

    /** Permanently removes a habit row. The CASCADE rule also deletes all its logs. */
    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long)

    /** Flips the enabled/disabled flag on a habit without touching any other data. */
    @Query("UPDATE habits SET is_enabled = :enabled WHERE id = :habitId")
    suspend fun setEnabled(habitId: Long, enabled: Boolean)

    // ─── Log Operations ──────────────────────────────────────────────────────

    /**
     * Writes a new completion log entry.
     * `OnConflictStrategy.IGNORE` means: if a log already exists for this habit+date,
     * do nothing — preventing duplicate completions on the same day.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity)

    /** Removes a completion log — used when the user un-checks a habit. */
    @Query("DELETE FROM habit_logs WHERE habit_id = :habitId AND date_completed = :dateCompleted")
    suspend fun deleteLog(habitId: Long, dateCompleted: Long)

    /** Returns a live stream of all completion logs for one habit, newest first. */
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date_completed DESC")
    fun getLogsForHabit(habitId: Long): Flow<List<HabitLogEntity>>

    /** Returns a live stream of every log across all habits — used to calculate streaks globally. */
    @Query("SELECT * FROM habit_logs ORDER BY date_completed DESC")
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
        return if (existing != null) {
            deleteLog(habitId, dateCompleted)
            false  // Was completed, now un-completed
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
}
