package com.habitflow.app

import android.app.Application
import com.habitflow.app.data.local.HabitDatabase
import com.habitflow.app.data.local.ThemePreferences
import com.habitflow.app.data.repository.RoomHabitRepository
import com.habitflow.app.domain.repository.HabitRepository

/**
 * FEATURE A — Architecture & Structure
 *
 * HabitFlowApplication is the entry point of the app.
 * Android creates one instance of this class when the app starts,
 * and it lives for the entire lifetime of the app.
 *
 * Think of it as the "central hub" that sets up all shared resources
 * (database, repository, preferences) so every screen can use them.
 */
class HabitFlowApplication : Application() {

    // ── Database ─────────────────────────────────────────────────────────────
    // `by lazy` means this is only created the first time it's actually needed,
    // saving memory if the app starts but doesn't access data immediately.
    val database: HabitDatabase by lazy { HabitDatabase.getInstance(this) }

    // ── Repository ───────────────────────────────────────────────────────────
    // The repository is the single source of truth for habit data.
    // All screens go through HabitRepository — they never touch the database directly.
    // This follows the Repository Pattern from the MVVM architecture.
    val repository: HabitRepository by lazy {
        RoomHabitRepository(database.habitDao())
    }

    // ── Theme Preferences ────────────────────────────────────────────────────
    // Stores the user's light/dark/system theme choice using DataStore,
    // which persists the setting even after the app is closed.
    val themePreferences: ThemePreferences by lazy { ThemePreferences(this) }
}
