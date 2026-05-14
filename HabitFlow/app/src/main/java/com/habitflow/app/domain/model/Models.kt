package com.habitflow.app.domain.model

import java.time.LocalDate

enum class HabitFrequency { DAILY, WEEKLY }

data class Habit(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val startDate: LocalDate = LocalDate.now(),
    val isEnabled: Boolean = true,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val color: String = "#6750A4",
    val reminderTime: String? = null,
    val reminderEnabled: Boolean = false,
)

data class DailyProgress(
    val date: LocalDate,
    val totalHabits: Int,
    val completedHabits: Int,
) {
    val completionRate: Float
        get() = if (totalHabits == 0) 0f else completedHabits.toFloat() / totalHabits
}
