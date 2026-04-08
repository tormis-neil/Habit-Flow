package com.habitflow.app.utils

import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.model.HabitFrequency
import com.habitflow.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * In-memory [HabitRepository] that replaces Room for the UI-only project.
 * All state lives in [MutableStateFlow]s so the ViewModels can
 * observe changes reactively, exactly as they would with a real database.
 */
class FakeDataSource : HabitRepository {

    // Seed habits shown on first launch
    private val seedHabits = listOf(
        Habit(id = 1, title = "Morning Meditation", description = "10 minutes of mindfulness", frequency = HabitFrequency.DAILY, startDate = LocalDate.now().minusDays(30), currentStreak = 7, longestStreak = 14, totalCompletions = 28, color = "#6750A4"),
        Habit(id = 2, title = "Read 20 Pages",      description = "Any book counts",            frequency = HabitFrequency.DAILY, startDate = LocalDate.now().minusDays(20), currentStreak = 3, longestStreak = 10, totalCompletions = 15, color = "#0288D1"),
        Habit(id = 3, title = "Evening Walk",        description = "At least 15 minutes",        frequency = HabitFrequency.DAILY, startDate = LocalDate.now().minusDays(14), currentStreak = 5, longestStreak = 5,  totalCompletions = 10, color = "#4CAF50"),
        Habit(id = 4, title = "Weekly Review",       description = "Plan the week ahead",        frequency = HabitFrequency.WEEKLY, startDate = LocalDate.now().minusDays(60), currentStreak = 4, longestStreak = 8,  totalCompletions = 8,  color = "#FF6D00"),
        Habit(id = 5, title = "Drink 2L Water",      description = "",                           frequency = HabitFrequency.DAILY, startDate = LocalDate.now().minusDays(10), currentStreak = 2, longestStreak = 3,  totalCompletions = 7,  color = "#00897B"),
    )

    private val _habits = MutableStateFlow(seedHabits)
    override val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    // Set of habit IDs completed today
    private val _completedTodayIds = MutableStateFlow(setOf(1L, 3L))
    override val completedTodayIds: StateFlow<Set<Long>> = _completedTodayIds.asStateFlow()

    // Completed dates per habit (for detail screen history)
    private val _completedDates = MutableStateFlow<Map<Long, List<LocalDate>>>(
        mapOf(
            1L to (0..27).map { LocalDate.now().minusDays(it.toLong()) }.filter { it.dayOfWeek.value < 6 },
            2L to (0..14).map { LocalDate.now().minusDays(it.toLong()) }.filter { it.dayOfWeek.value % 2 == 0 },
            3L to (0..9).map { LocalDate.now().minusDays(it.toLong()) },
            4L to listOf(LocalDate.now().minusWeeks(0), LocalDate.now().minusWeeks(1), LocalDate.now().minusWeeks(2)),
            5L to (0..6).map { LocalDate.now().minusDays(it.toLong()) },
        )
    )
    override val completedDates: StateFlow<Map<Long, List<LocalDate>>> = _completedDates.asStateFlow()

    private var nextId = 6L

    // ─── Mutations ────────────────────────────────────────────────────────────

    override suspend fun addHabit(habit: Habit): Long {
        val id = nextId++
        _habits.update { it + habit.copy(id = id) }
        return id
    }

    override suspend fun updateHabit(habit: Habit) {
        _habits.update { list -> list.map { if (it.id == habit.id) habit else it } }
    }

    override suspend fun deleteHabit(habitId: Long) {
        _habits.update { list -> list.filter { it.id != habitId } }
        _completedTodayIds.update { it - habitId }
        _completedDates.update { it - habitId }
    }

    override suspend fun setEnabled(habitId: Long, enabled: Boolean) {
        _habits.update { list ->
            list.map { if (it.id == habitId) it.copy(isEnabled = enabled) else it }
        }
    }

    override suspend fun toggleTodayCompletion(habitId: Long): Boolean {
        val isNowDone = habitId !in _completedTodayIds.value
        _completedTodayIds.update {
            if (isNowDone) it + habitId else it - habitId
        }
        // Update streak on the habit
        _habits.update { list ->
            list.map { habit ->
                if (habit.id != habitId) habit
                else {
                    val newStreak = if (isNowDone) habit.currentStreak + 1 else maxOf(0, habit.currentStreak - 1)
                    habit.copy(
                        currentStreak = newStreak,
                        longestStreak = maxOf(habit.longestStreak, newStreak),
                        totalCompletions = if (isNowDone) habit.totalCompletions + 1 else maxOf(0, habit.totalCompletions - 1),
                    )
                }
            }
        }
        return isNowDone
    }

    override suspend fun getHabitById(id: Long): Habit? = _habits.value.firstOrNull { it.id == id }

    override suspend fun getCompletedDates(habitId: Long): List<LocalDate> =
        _completedDates.value[habitId] ?: emptyList()

    /** Build a 7-element list of booleans (Mon..Sun) for the current week. */
    override suspend fun getWeeklyProgress(habitId: Long): List<Boolean> {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
        val dates = _completedDates.value[habitId]?.toSet() ?: emptySet()
        return (0..6).map { weekStart.plusDays(it.toLong()) in dates }
    }
}
