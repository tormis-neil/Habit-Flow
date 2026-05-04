package com.habitflow.app.data.repository

import com.habitflow.app.data.local.dao.HabitDao
import com.habitflow.app.data.local.entity.HabitLogEntity
import com.habitflow.app.data.local.entity.toHabit
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.model.HabitFrequency
import com.habitflow.app.domain.repository.HabitRepository
import com.habitflow.app.domain.util.StreakCalculator
import com.habitflow.app.data.local.entity.HabitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

import javax.inject.Inject

/**
 * FEATURE B — Local Data Persistence (Room)
 *
 * RoomHabitRepository is the PRODUCTION implementation of HabitRepository.
 * It is the bridge between the Room database (raw rows) and the rest of the app
 * (domain models that ViewModels and UI understand).
 *
 * Key responsibilities:
 *  1. Listens to the database in real-time using Kotlin Flows.
 *  2. Calculates streak counts and merges them into the habit models.
 *  3. Exposes everything to ViewModels as StateFlows (live, observable data streams).
 *  4. Handles all write operations (add, update, delete, toggle completion).
 *
 * All database work runs on `Dispatchers.IO` — a background thread —
 * so the UI (main thread) is never blocked or frozen.
 */
class RoomHabitRepository @Inject constructor(
    private val dao: HabitDao,
) : HabitRepository {

    // Background scope for collecting database streams — runs independently of any ViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Exposed StateFlows ──────────────────────────────────────────────────
    // These are the "output" of this repository. Any ViewModel that collects
    // these will automatically receive fresh data whenever the database changes.

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    override val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    private val _completedTodayIds = MutableStateFlow<Set<Long>>(emptySet())
    override val completedTodayIds: StateFlow<Set<Long>> = _completedTodayIds.asStateFlow()

    private val _completedDates = MutableStateFlow<Map<Long, List<LocalDate>>>(emptyMap())
    override val completedDates: StateFlow<Map<Long, List<LocalDate>>> = _completedDates.asStateFlow()

    init {
        // Start listening to two database streams at once: all habits AND all logs.
        // `combine` merges them — every time either table changes, this block re-runs.
        scope.launch {
            combine(dao.getAllHabits(), dao.getAllLogs()) { entities, logs ->

                // Group logs by which habit they belong to
                val logsByHabit = logs.groupBy { it.habitId }
                val today = LocalDate.now().toEpochDay()

                // Find which habits the user has already completed today
                val todayIds = logs
                    .filter { it.dateCompleted == today }
                    .map { it.habitId }
                    .toSet()
                _completedTodayIds.value = todayIds

                // Build a map of habit ID → sorted list of all completion dates
                val datesMap = logsByHabit.mapValues { (_, habitLogs) ->
                    habitLogs.map { LocalDate.ofEpochDay(it.dateCompleted) }
                        .distinct()
                        .sortedDescending()
                }
                _completedDates.value = datesMap

                // For each habit, calculate its streak using its completion dates,
                // then convert the raw database row into a full domain Habit object
                entities.map { entity ->
                    val habitDates = datesMap[entity.id] ?: emptyList()
                    val freq = try { HabitFrequency.valueOf(entity.frequency) } catch (_: Exception) { HabitFrequency.DAILY }
                    val streaks = StreakCalculator.calculate(habitDates, freq)
                    entity.toHabit(
                        currentStreak = streaks.currentStreak,
                        longestStreak = streaks.longestStreak,
                        totalCompletions = habitDates.size,
                    )
                }
            }.collect { _habits.value = it } // Push the fully-formed habit list to StateFlow
        }
    }

    // ─── Mutations ───────────────────────────────────────────────────────────

    /** Creates a new habit in the database and returns its auto-assigned ID. */
    override suspend fun addHabit(habit: Habit): Long {
        val entity = HabitEntity(
            title = habit.title,
            description = habit.description,
            frequency = habit.frequency.name,
            startDate = habit.startDate.toEpochDay(),
            isEnabled = habit.isEnabled,
            color = habit.color,
        )
        return dao.upsertHabit(entity)
    }

    /** Saves an updated habit, preserving its existing ID so it overwrites the old row. */
    override suspend fun updateHabit(habit: Habit) {
        dao.upsertHabit(
            HabitEntity(
                id = habit.id, // ID must be included so Room knows which row to update
                title = habit.title,
                description = habit.description,
                frequency = habit.frequency.name,
                startDate = habit.startDate.toEpochDay(),
                isEnabled = habit.isEnabled,
                color = habit.color,
            )
        )
    }

    /** Permanently deletes a habit — the associated logs are removed automatically via CASCADE. */
    override suspend fun deleteHabit(habitId: Long) {
        dao.deleteHabit(habitId)
    }

    /** Flips the habit's active/paused state in the database. */
    override suspend fun setEnabled(habitId: Long, enabled: Boolean) {
        dao.setEnabled(habitId, enabled)
    }

    /**
     * Toggles today's completion for a habit.
     * Delegates to the atomic DAO transaction to guarantee no duplicate logs.
     */
    override suspend fun toggleTodayCompletion(habitId: Long): Boolean {
        val today = LocalDate.now().toEpochDay()
        return dao.toggleLog(habitId, today, System.currentTimeMillis())
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    /** Loads a single habit by ID, with streaks calculated from the in-memory dates cache. */
    override suspend fun getHabitById(id: Long): Habit? {
        val entity = dao.getHabitById(id) ?: return null
        val dates = _completedDates.value[id] ?: emptyList()
        val freq = try { HabitFrequency.valueOf(entity.frequency) } catch (_: Exception) { HabitFrequency.DAILY }
        val streaks = StreakCalculator.calculate(dates, freq)
        return entity.toHabit(
            currentStreak = streaks.currentStreak,
            longestStreak = streaks.longestStreak,
            totalCompletions = dates.size,
        )
    }

    /** Returns all dates a habit was completed, from the in-memory cache. */
    override suspend fun getCompletedDates(habitId: Long): List<LocalDate> {
        return _completedDates.value[habitId] ?: emptyList()
    }

    /**
     * Returns a 7-element list (Mon → Sun) where `true` = completed that day this week.
     * Used to render the weekly dot row on the Habit Detail screen.
     */
    override suspend fun getWeeklyProgress(habitId: Long): List<Boolean> {
        val today = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY) // Always start from Monday
        val dates = (_completedDates.value[habitId] ?: emptyList()).toSet()
        // For each day of the week, check if it appears in the completion history
        return (0..6).map { weekStart.plusDays(it.toLong()) in dates }
    }
}
