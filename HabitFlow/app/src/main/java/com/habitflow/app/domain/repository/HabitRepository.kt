package com.habitflow.app.domain.repository

import com.habitflow.app.domain.model.Habit
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * FEATURE A — Architecture & Structure
 *
 * HabitRepository is the CONTRACT (interface) that defines what operations
 * are available for habit data. It sits in the domain layer — the middle
 * ground between the UI and the database.
 *
 * Why an interface?
 * - Screens and ViewModels only know about this interface, not WHERE data comes from.
 * - In production the data comes from Room (RoomHabitRepository).
 * - For testing, a fake data source can be swapped in without changing any screen code.
 * - This is the core of the Repository Pattern in MVVM architecture.
 */
interface HabitRepository {

    // ── Live Data Streams ─────────────────────────────────────────────────────
    // StateFlow is a live data stream — any screen that "collects" these will
    // automatically refresh whenever the data changes, without needing to reload.

    /** A live list of all habits, with streak counts already calculated. */
    val habits: StateFlow<List<Habit>>

    /** A live set of habit IDs that have been marked complete today. */
    val completedTodayIds: StateFlow<Set<Long>>

    /** A live map of every habit's full completion history, keyed by habit ID. */
    val completedDates: StateFlow<Map<Long, List<LocalDate>>>

    // ── Write Operations ──────────────────────────────────────────────────────
    // `suspend` means these run on a background thread so the UI never freezes.

    suspend fun addHabit(habit: Habit): Long       // Save a new habit
    suspend fun updateHabit(habit: Habit)           // Save changes to an existing habit
    suspend fun deleteHabit(habitId: Long)          // Permanently remove a habit and its logs
    suspend fun setEnabled(habitId: Long, enabled: Boolean) // Pause or reactivate a habit

    /**
     * Toggles today's completion for a habit (done → undone → done...).
     * Returns true if the habit is now marked complete, false if unmarked.
     */
    suspend fun toggleTodayCompletion(habitId: Long): Boolean

    // ── Read Operations ───────────────────────────────────────────────────────

    suspend fun getHabitById(id: Long): Habit?                      // Fetch one habit by ID
    suspend fun getCompletedDates(habitId: Long): List<LocalDate>   // Full completion history

    /** Returns 7 booleans (Monday → Sunday) showing which days this week the habit was done. */
    suspend fun getWeeklyProgress(habitId: Long): List<Boolean>
}
