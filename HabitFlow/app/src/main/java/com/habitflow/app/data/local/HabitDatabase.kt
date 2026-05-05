package com.habitflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.habitflow.app.data.local.dao.HabitDao
import com.habitflow.app.data.local.entity.HabitEntity
import com.habitflow.app.data.local.entity.HabitLogEntity
import com.habitflow.app.data.local.entity.UserProfileEntity
import com.habitflow.app.data.local.dao.UserDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

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
 *  - Which tables exist: HabitEntity, HabitLogEntity, UserProfileEntity
 *  - The current version (3) — incremented whenever the table structure changes
 *  - exportSchema = true → saves a JSON snapshot of the schema for version history
 */
@Database(
    entities = [HabitEntity::class, HabitLogEntity::class, UserProfileEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class) // Handles converting complex types (e.g. LocalDate) for storage
abstract class HabitDatabase : RoomDatabase() {

    // The DAO (query object) for all habit and log operations
    abstract fun habitDao(): HabitDao
    
    abstract fun userDao(): UserDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create user_profile table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profile` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `username` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`uid`))"
                )
                
                // 2. Add columns to habits
                db.execSQL("ALTER TABLE habits ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN `user_id` TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN `is_synced` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN `reminder_time` TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN `reminder_enabled` INTEGER NOT NULL DEFAULT 0")

                // Update existing habits with unique UUIDs and current timestamp
                val currentTime = System.currentTimeMillis()
                db.execSQL("UPDATE habits SET updated_at = $currentTime")
                val habitsCursor = db.query("SELECT id FROM habits WHERE uuid = ''")
                while (habitsCursor.moveToNext()) {
                    val id = habitsCursor.getLong(0)
                    val uuid = UUID.randomUUID().toString()
                    db.execSQL("UPDATE habits SET uuid = '$uuid' WHERE id = $id")
                }
                habitsCursor.close()

                // 3. Add columns to habit_logs
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN `user_id` TEXT")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN `is_deleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN `is_synced` INTEGER NOT NULL DEFAULT 0")
                
                db.execSQL("UPDATE habit_logs SET updated_at = $currentTime")
                val logsCursor = db.query("SELECT logId FROM habit_logs WHERE uuid = ''")
                while (logsCursor.moveToNext()) {
                    val id = logsCursor.getLong(0)
                    val uuid = UUID.randomUUID().toString()
                    db.execSQL("UPDATE habit_logs SET uuid = '$uuid' WHERE logId = $id")
                }
                logsCursor.close()
            }
        }
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
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
