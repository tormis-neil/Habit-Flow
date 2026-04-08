package com.habitflow.app.domain.util

import com.habitflow.app.domain.model.HabitFrequency

/**
 * Shared constants for habit-tracking logic.
 */
object HabitConstants {

    /** A daily habit should be completed 7 times per week. */
    const val DAILY_TARGET_PER_WEEK = 7

    /** A weekly habit should be completed 1 time per week. */
    const val WEEKLY_TARGET_PER_WEEK = 1

    /** Returns the weekly completion target for the given [frequency]. */
    fun weeklyTarget(frequency: HabitFrequency): Int = when (frequency) {
        HabitFrequency.DAILY -> DAILY_TARGET_PER_WEEK
        HabitFrequency.WEEKLY -> WEEKLY_TARGET_PER_WEEK
    }
}
