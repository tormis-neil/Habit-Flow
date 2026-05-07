package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.repository.HabitRepository
import com.habitflow.app.domain.util.HabitConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FEATURE D — State & User Interaction
 *
 * ProgressUiState holds all data the Progress screen needs in one bundle.
 * `weeklyProgressMap` is pre-computed here (not in the screen) so the UI
 * only receives ready-to-render values and has no business logic of its own.
 */
data class ProgressUiState(
    val habits: List<Habit> = emptyList(),                      // All habits (enabled + disabled)
    val totalStreakDays: Int = 0,                               // Sum of current streaks across all habits
    val weeklyCompletionRate: Float = 0f,                       // 0.0–1.0 average across all habits
    /** Pre-computed Mon–Sun booleans per habit — avoids recalculating in the screen. */
    val weeklyProgressMap: Map<Long, List<Boolean>> = emptyMap(),
)

/**
 * FEATURE D — State & User Interaction
 *
 * ProgressViewModel feeds data to the Progress screen.
 * It observes the shared habit list from the repository and derives
 * three aggregated metrics whenever habits or logs change:
 *
 *  1. `totalStreakDays`     — adds up every habit's current streak.
 *  2. `weeklyProgressMap`  — asks the repository for Mon–Sun completion for each habit.
 *  3. `weeklyCompletionRate` — averages each habit's weekly completion fraction (0–1).
 *
 * This keeps the Progress screen purely declarative — it only renders numbers,
 * never computes them.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: HabitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        // Every time the habits stream emits (a habit is added, edited, or a completion is toggled),
        // this block re-runs to recompute all progress metrics.
        viewModelScope.launch {
            repository.habits.collect { habits ->

                // Sum of all active streaks — e.g. [5, 3, 0, 2] → 10 total streak days
                val totalStreak = habits.sumOf { it.currentStreak }

                // Fetch Mon–Sun completion for each habit and store by habit ID
                val progressMap = habits.associate { habit ->
                    habit.id to repository.getWeeklyProgress(habit.id)
                }

                // For each habit, calculate what fraction of the weekly target was met.
                // `weeklyTarget` = 7 for daily habits, 1 for weekly habits.
                val weeklyRates = habits.map { habit ->
                    val done   = progressMap[habit.id]?.count { it } ?: 0
                    val target = HabitConstants.weeklyTarget(habit.frequency)
                    done.toFloat() / target // e.g. 5 out of 7 = 0.71
                }

                _uiState.update {
                    ProgressUiState(
                        habits               = habits,
                        totalStreakDays       = totalStreak,
                        // Average completion rate across all habits; 0 if no habits exist
                        weeklyCompletionRate = if (weeklyRates.isEmpty()) 0f else weeklyRates.average().toFloat(),
                        weeklyProgressMap    = progressMap,
                    )
                }
            }
        }
    }
}
