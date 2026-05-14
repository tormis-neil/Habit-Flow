package com.habitflow.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.model.HabitFrequency
import java.time.LocalDate
import java.util.UUID

/**
 * FEATURE B — Local Data Persistence (Room)
 *
 * HabitEntity represents a single ROW in the "habits" table inside the app's database.
 * Every habit the user creates is stored here and survives app restarts.
 *
 * Think of `@Entity` as defining a spreadsheet:
 *  - `@PrimaryKey` is the unique row ID (auto-assigned by the database)
 *  - Each field below is a column in that spreadsheet
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,           // Unique identifier, auto-assigned (0 = "not yet saved")

    val title: String,          // The habit's name, e.g. "Morning Run"
    val description: String = "", // Optional detail, e.g. "Run 5km before breakfast"

    // Stored as "DAILY" or "WEEKLY" text so it's human-readable in the database
    val frequency: String = HabitFrequency.DAILY.name,

    // Dates are stored as a number (days since Jan 1, 1970) for easy sorting/comparison
    @ColumnInfo(name = "start_date")
    val startDate: Long = LocalDate.now().toEpochDay(),

    // When false, the habit is "paused" and hidden from the daily list
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    // The habit's accent color stored as a hex string, e.g. "#6750A4"
    val color: String = "#6750A4",

    // --- Sync Metadata ---
    val uuid: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "user_id")
    val userId: String? = null,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    // --- Reminders ---
    @ColumnInfo(name = "reminder_time")
    val reminderTime: String? = null,
    
    @ColumnInfo(name = "reminder_enabled")
    val reminderEnabled: Boolean = false,
)

/**
 * Converts a database row (HabitEntity) → app model (Habit).
 * Streak numbers are passed in separately because they are calculated
 * from the logs table, not stored directly on the habit.
 */
fun HabitEntity.toHabit(
    currentStreak: Int = 0,
    longestStreak: Int = 0,
    totalCompletions: Int = 0,
) = Habit(
    id = id,
    title = title,
    description = description,
    // Safely converts the stored text back to the enum; defaults to DAILY if invalid
    frequency = try { HabitFrequency.valueOf(frequency) } catch (_: Exception) { HabitFrequency.DAILY },
    startDate = LocalDate.ofEpochDay(startDate), // Convert number back to a readable date
    isEnabled = isEnabled,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    totalCompletions = totalCompletions,
    color = color,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled
)

/** Converts an app model (Habit) → database row (HabitEntity) before saving. */
fun Habit.toEntity(
    uuid: String = UUID.randomUUID().toString(),
    userId: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
    isDeleted: Boolean = false,
    isSynced: Boolean = false,
    reminderTime: String? = null,
    reminderEnabled: Boolean = false
) = HabitEntity(
    id = id,
    title = title,
    description = description,
    frequency = frequency.name,
    startDate = startDate.toEpochDay(), // Convert date to a number for storage
    isEnabled = isEnabled,
    color = color,
    uuid = uuid,
    userId = userId,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    isSynced = isSynced,
    reminderTime = reminderTime,
    reminderEnabled = reminderEnabled
)
