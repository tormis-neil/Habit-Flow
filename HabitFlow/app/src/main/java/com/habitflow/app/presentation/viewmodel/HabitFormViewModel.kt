package com.habitflow.app.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitflow.app.domain.model.Habit
import com.habitflow.app.domain.model.HabitFrequency
import com.habitflow.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.habitflow.app.R

/**
 * FEATURE D — State & User Interaction
 *
 * HabitFormUiState holds everything the Create/Edit form screen needs.
 * `titleError` carries a string resource ID (not a raw string) — this lets
 * the screen display a localized error message without hardcoding any text.
 * `isSaved` acts as a one-shot signal: once true, the screen navigates away.
 */
data class HabitFormUiState(
    val title: String = "",                              // Current text in the title field
    val description: String = "",                        // Current text in the description field
    val frequency: HabitFrequency = HabitFrequency.DAILY, // Selected frequency chip
    val color: String = "#6750A4",                       // Selected color hex value
    val isEditing: Boolean = false,                      // True = edit mode, False = create mode
    val isSaved: Boolean = false,                        // Triggers navigation back when true
    val titleError: Int? = null,                         // String resource ID for validation error
)

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * FEATURE D — State & User Interaction
 *
 * HabitFormViewModel manages the state for the habit create AND edit form.
 *
 * Mode detection:
 *  - The route includes a `habitId` when editing (e.g. /habit/5/edit).
 *  - If `habitId` is -1 (the default), the form is in CREATE mode.
 *  - If it's a real ID, the ViewModel loads that habit and pre-fills the fields.
 *
 * Input handling:
 *  - Each `onXxxChange()` function updates the relevant field in `uiState`.
 *    Because `uiState` is a StateFlow, the screen rebuilds instantly on each keystroke.
 *
 * Save logic (`onSave`):
 *  1. Validate — title must not be blank (shows an inline error if it is).
 *  2. If editing, fetch the existing habit and update only the changed fields.
 *  3. If creating, build a brand new Habit with today as the start date.
 *  4. Set `isSaved = true` to signal the screen to navigate back.
 */
@HiltViewModel
class HabitFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
) : ViewModel() {

    // -1 signals "no habit ID" (create mode); any other value means edit mode
    private val habitId: Long = savedStateHandle["habitId"] ?: -1L
    private val isEditing = habitId != -1L

    private val _uiState = MutableStateFlow(HabitFormUiState(isEditing = isEditing))
    val uiState: StateFlow<HabitFormUiState> = _uiState.asStateFlow()

    init {
        // In edit mode, load the existing habit data to pre-fill the form fields
        if (isEditing) {
            viewModelScope.launch {
                repository.getHabitById(habitId)?.let { habit ->
                    _uiState.update {
                        it.copy(
                            title       = habit.title,
                            description = habit.description,
                            frequency   = habit.frequency,
                            color       = habit.color,
                        )
                    }
                }
            }
        }
    }

    // ── Input event handlers ──────────────────────────────────────────────────
    // Each function uses `update { }` to create a new copy of the state with only
    // the changed field modified — all other fields remain the same.
    fun onTitleChange(v: String)          = _uiState.update { it.copy(title = v, titleError = null) } // Clears error as user types
    fun onDescriptionChange(v: String)    = _uiState.update { it.copy(description = v) }
    fun onFrequencyChange(v: HabitFrequency) = _uiState.update { it.copy(frequency = v) }
    fun onColorChange(v: String)          = _uiState.update { it.copy(color = v) }

    /** Validates the form and, if valid, saves the habit to the database. */
    fun onSave() {
        val s = _uiState.value

        // Validation: title is required — show an inline error and stop if blank
        if (s.title.isBlank()) {
            _uiState.update { it.copy(titleError = R.string.habit_title_error) }
            return
        }

        viewModelScope.launch {
            if (isEditing) {
                // EDIT: Fetch the full existing habit (preserves fields like startDate)
                // then overwrite only the fields changed in the form
                repository.getHabitById(habitId)?.let { existing ->
                    repository.updateHabit(
                        existing.copy(
                            title       = s.title.trim(),
                            description = s.description.trim(),
                            frequency   = s.frequency,
                            color       = s.color,
                        )
                    )
                }
            } else {
                // CREATE: Build a new Habit from scratch, using today as the start date
                repository.addHabit(
                    Habit(
                        title       = s.title.trim(),
                        description = s.description.trim(),
                        frequency   = s.frequency,
                        startDate   = LocalDate.now(),
                        color       = s.color,
                    )
                )
            }
            // Signal the screen that saving is done — triggers navigation back
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
