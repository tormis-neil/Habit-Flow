package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * FEATURE D — State & User Interaction
 *
 * HabitDetailUiState holds all data the Habit Detail screen needs.
 * The `isDeleted` flag is a one-way signal — when set to true, the screen
 * knows to navigate back without needing a direct callback into the ViewModel.
 */
data class HabitDetailUiState(
    val habit: Habit? = null,                            // null while loading from the database
    val isCompletedToday: Boolean = false,               // Is today's completion logged?
    val weeklyProgress: List<Boolean> = List(7) { false }, // Mon–Sun completion booleans
    val completedDates: List<LocalDate> = emptyList(),   // Full history of completion dates
    val isDeleted: Boolean = false,                      // Signals the screen to navigate back
)

/**
 * FEATURE D — State & User Interaction
 *
 * HabitDetailViewModel manages state for the Habit Detail screen.
 *
 * It receives the `habitId` from the navigation route via `SavedStateHandle`
 * (a key-value store that survives process death and screen rotation).
 *
 * Two background coroutines run in parallel:
 *  1. Watches the full habit list — updates habit data and weekly/date history when it changes.
 *  2. Watches completedTodayIds — updates the "completed today" button state independently.
 *
 * User actions (toggle, enable/disable, delete) are dispatched to the repository
 * as suspend calls on the IO thread, keeping the UI thread free.
 */
class HabitDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
) : ViewModel() {

    // Read the habitId from the navigation route — crashes loudly if missing (programming error)
    private val habitId: Long = checkNotNull(savedStateHandle["habitId"])

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    init {
        // Coroutine 1: Watch for habit data changes (streaks, details, history)
        viewModelScope.launch {
            repository.habits.collect { habits ->
                // Find this specific habit from the full list by its ID
                val habit = habits.firstOrNull { it.id == habitId }
                _uiState.update {
                    it.copy(
                        habit = habit,
                        weeklyProgress = repository.getWeeklyProgress(habitId),  // Mon–Sun booleans
                        completedDates = repository.getCompletedDates(habitId),  // Full history
                    )
                }
            }
        }

        // Coroutine 2: Watch for today's completion state separately
        // (this updates the button appearance without needing a full habit reload)
        viewModelScope.launch {
            repository.completedTodayIds.collect { ids ->
                _uiState.update { it.copy(isCompletedToday = habitId in ids) }
            }
        }
    }

    /** Toggles today's completion — called when the user taps the "Mark Complete" button. */
    fun onToggleToday() {
        viewModelScope.launch {
            repository.toggleTodayCompletion(habitId)
            // No need to update state manually — repository emits to streams, which update uiState
        }
    }

    /** Flips the habit's enabled state — called when the user toggles the active switch. */
    fun onToggleEnabled() {
        val current = _uiState.value.habit?.isEnabled ?: return // Do nothing if habit not loaded
        viewModelScope.launch {
            repository.setEnabled(habitId, !current)
        }
    }

    /**
     * Permanently deletes the habit and all its logs.
     * Sets `isDeleted = true` on the uiState — the screen's `LaunchedEffect`
     * detects this and triggers navigation back automatically.
     */
    fun onDeleteHabit() {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
            _uiState.update { it.copy(isDeleted = true) } // Signal the screen to go back
        }
    }
}
