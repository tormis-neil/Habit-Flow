package com.habitflow.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * FEATURE B — Local Data Persistence (Room)
 *
 * HabitLogEntity represents one COMPLETION EVENT — every time a user marks
 * a habit as done, a new row is written to the "habit_logs" table.
 *
 * Key design decisions:
 *  1. FOREIGN KEY — each log must belong to an existing habit.
 *     If that habit is deleted, ALL its logs are automatically deleted too (CASCADE).
 *  2. UNIQUE INDEX — prevents logging the same habit twice on the same day.
 *     Trying to insert a duplicate is silently ignored (OnConflict = IGNORE).
 */
@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],          // References HabitEntity.id
            childColumns = ["habit_id"],     // This column must match an existing habit
            onDelete = ForeignKey.CASCADE,   // Auto-delete logs when the parent habit is removed
        )
    ],
    indices = [
        Index("habit_id"),                                              // Speeds up "find all logs for habit X"
        Index(value = ["habit_id", "date_completed"], unique = true),  // One log per habit per day
    ],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,                    // Unique ID for this completion event

    @ColumnInfo(name = "habit_id")
    val habitId: Long,                      // Which habit was completed

    // The date stored as a number (epoch day) — only the DATE, not the time
    @ColumnInfo(name = "date_completed")
    val dateCompleted: Long,

    // The exact moment the user tapped "complete", stored as a Unix timestamp (milliseconds)
    val timestamp: Long = System.currentTimeMillis(),

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
)
