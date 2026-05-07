package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.domain.model.DailyProgress
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.model.HabitFrequency
import com.habitflow.app.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * FEATURE D — State & User Interaction
 *
 * HomeUiState is a single snapshot of everything the Home screen needs to render.
 * Grouping all screen data into one data class means the UI only ever gets ONE
 * consistent update — no risk of the habit list and progress bar going out of sync.
 */
data class HomeUiState(
    val habits: List<Habit> = emptyList(),           // Habits visible for the selected date
    val completedHabitIds: Set<Long> = emptySet(),   // Habits the user has ticked off today
    val dailyProgress: DailyProgress = DailyProgress(LocalDate.now(), 0, 0), // Progress bar data
    val selectedDate: LocalDate = LocalDate.now(),   // Which date the chip row is showing
)

/**
 * FEATURE D — State & User Interaction
 *
 * HomeViewModel holds the BUSINESS LOGIC for the Home screen.
 * It sits between the repository (data) and the screen (UI).
 *
 * The ViewModel:
 *  1. Combines three live streams — habits, completedTodayIds, and selectedDate.
 *  2. Filters habits for the selected date and frequency.
 *  3. Computes daily progress (how many habits done out of how many).
 *  4. Exposes a single `uiState` StateFlow that HomeScreen observes.
 *  5. Handles user actions: toggling a habit complete and changing the selected date.
 *
 * Because it extends `ViewModel`, Android keeps it alive during screen rotations.
 * All coroutines run in `viewModelScope` — they cancel automatically when
 * the user permanently leaves the screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HabitRepository
) : ViewModel() {

    // Tracks which date the user has selected in the date chip row
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    // The single source of truth for the Home screen — starts empty, then populates
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // `combine` listens to ALL THREE streams simultaneously.
        // Any time habits change, logs change, or the user picks a new date —
        // this block re-runs and pushes a fresh HomeUiState to the screen.
        viewModelScope.launch {
            combine(
                repository.habits,
                repository.completedTodayIds,
                _selectedDate,
            ) { habits, completedIds, selectedDate ->

                // Filter habits to only those relevant for the selected date and frequency
                val habitsForDate = habits.filter { habit ->
                    habit.isEnabled && when (habit.frequency) {
                        HabitFrequency.DAILY  -> true // Daily habits appear every day
                        HabitFrequency.WEEKLY -> selectedDate.dayOfWeek == habit.startDate.dayOfWeek // Weekly habits appear on their designated day
                    }
                }

                // Completion state is only meaningful for today.
                // For past/future dates we show all habits as unchecked.
                val relevantCompletedIds = if (selectedDate == LocalDate.now()) {
                    completedIds
                } else {
                    emptySet()
                }

                HomeUiState(
                    habits = habitsForDate,
                    completedHabitIds = relevantCompletedIds,
                    dailyProgress = DailyProgress(
                        date          = selectedDate,
                        totalHabits   = habitsForDate.size,
                        // Count how many of today's completed IDs belong to the filtered list
                        completedHabits = relevantCompletedIds.count { id ->
                            habitsForDate.any { it.id == id }
                        },
                    ),
                    selectedDate = selectedDate,
                )
            }.collect { _uiState.value = it } // Push new state to the UI
        }
    }

    /** Called when the user taps the checkbox on a habit card — toggles today's completion. */
    fun onToggleHabit(habitId: Long) {
        viewModelScope.launch {
            repository.toggleTodayCompletion(habitId) // Runs on background thread via repository
        }
    }

    /** Called when the user taps a date chip — updates the daily view to that date. */
    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date // Triggers the `combine` above to re-emit
    }
}