package com.habitflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.habitflow.app.data.local.dao.HabitDao
import com.habitflow.app.data.local.entity.HabitEntity
import com.habitflow.app.data.local.entity.HabitLogEntity

/**
 * FEATURE B — Local Data Persistence (Room)
 *
 * HabitDatabase is the MAIN DATABASE CLASS for the app.
 * It ties together all the tables (entities) and query objects (DAOs).
 *
 * Room is Android's official library for local database storage — it wraps
 * SQLite (the same database engine used in most mobile apps) with type-safe
 * Kotlin code so we never write raw SQL for setup.
 *
 * @Database tells Room:
 *  - Which tables exist: HabitEntity (habits table) + HabitLogEntity (habit_logs table)
 *  - The current version (2) — incremented whenever the table structure changes
 *  - exportSchema = true → saves a JSON snapshot of the schema for version history
 */
@Database(
    entities = [HabitEntity::class, HabitLogEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class) // Handles converting complex types (e.g. LocalDate) for storage
abstract class HabitDatabase : RoomDatabase() {

    // The DAO (query object) for all habit and log operations
    abstract fun habitDao(): HabitDao

    companion object {
        // @Volatile ensures all threads always see the latest value of INSTANCE
        @Volatile
        private var INSTANCE: HabitDatabase? = null

        /**
         * SINGLETON pattern — ensures only ONE database connection exists app-wide.
         * If it's already created, return the existing one; otherwise build it.
         * `synchronized` prevents two threads from creating the database at the same time.
         */
        fun getInstance(context: Context): HabitDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "habitflow_database", // The file name of the database on the device
                )
                    // If the database version changes and no migration is provided,
                    // delete and rebuild from scratch (data loss — acceptable during development)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
