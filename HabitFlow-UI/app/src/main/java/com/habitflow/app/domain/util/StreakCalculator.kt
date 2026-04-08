package com.habitflow.app.domain.util

import com.habitflow.app.domain.model.HabitFrequency
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The result of a streak calculation: how long the current run is, and the all-time best. */
data class StreakResult(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

/**
 * FEATURE B — Local Data Persistence (Room)
 *
 * StreakCalculator computes STREAK COUNTS from a list of completion dates.
 * A streak is the number of consecutive time periods (days or weeks) that a
 * habit has been completed without a break.
 *
 * This is a pure calculation object — it has no side effects and does not
 * touch the database. It works entirely with the list of dates passed to it.
 *
 * Two modes:
 *  - DAILY:  counts consecutive calendar days (e.g. Mon, Tue, Wed = streak of 3)
 *  - WEEKLY: counts consecutive ISO weeks    (e.g. Week 10, 11, 12 = streak of 3)
 */
object StreakCalculator {

    fun calculate(
        completedDates: List<LocalDate>,
        frequency: HabitFrequency = HabitFrequency.DAILY,
    ): StreakResult {
        if (completedDates.isEmpty()) return StreakResult() // No completions → no streak
        return when (frequency) {
            HabitFrequency.DAILY -> calculateDaily(completedDates)
            HabitFrequency.WEEKLY -> calculateWeekly(completedDates)
        }
    }

    // ─── Daily Streak ─────────────────────────────────────────────────────────

    private fun calculateDaily(dates: List<LocalDate>): StreakResult {
        // Remove duplicates and sort from earliest to latest
        val sorted = dates.distinct().sorted()

        var longest = 1
        var current = 1

        // Walk through dates checking if each one is exactly 1 day after the previous
        for (i in 1 until sorted.size) {
            if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) {
                current++ // Consecutive day → extend the streak
            } else {
                current = 1 // Gap found → reset the streak counter
            }
            if (current > longest) longest = current
        }

        // A streak is only "active" if the last completion was today OR yesterday.
        // Otherwise the streak has been broken (user missed a day).
        val today = LocalDate.now()
        val lastDate = sorted.last()
        val activeStreak = if (lastDate == today || lastDate == today.minusDays(1)) {
            current
        } else {
            0 // The streak exists historically but is no longer active
        }

        return StreakResult(currentStreak = activeStreak, longestStreak = longest)
    }

    // ─── Weekly Streak ────────────────────────────────────────────────────────

    private fun calculateWeekly(dates: List<LocalDate>): StreakResult {
        // Convert each date to an ISO week identifier (e.g. 2026 week 12 → 202612)
        // This groups multiple completions in the same week into one "counted" week
        val weeks = dates.map {
            it.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 +
            it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }.distinct().sorted()

        if (weeks.isEmpty()) return StreakResult()

        var longest = 1
        var current = 1

        // Walk through weeks checking if each one immediately follows the previous
        for (i in 1 until weeks.size) {
            val prevYear = weeks[i - 1] / 100
            val prevWeek = weeks[i - 1] % 100
            val curYear  = weeks[i] / 100
            val curWeek  = weeks[i] % 100

            // Consecutive means: same year and week+1, OR year rolled over and week reset to 1
            val isConsecutive = (curYear == prevYear && curWeek == prevWeek + 1) ||
                    (curYear == prevYear + 1 && prevWeek >= 52 && curWeek == 1)

            if (isConsecutive) {
                current++
            } else {
                current = 1 // Non-consecutive week → reset
            }
            if (current > longest) longest = current
        }

        // Active if the last recorded week is the current week or last week
        val today = LocalDate.now()
        val currentWeekKey = today.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 + today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val lastWeekDate = today.minusWeeks(1)
        val lastWeekKey = lastWeekDate.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 + lastWeekDate.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        val activeStreak = if (weeks.last() == currentWeekKey || weeks.last() == lastWeekKey) {
            current
        } else {
            0
        }

        return StreakResult(currentStreak = activeStreak, longestStreak = longest)
    }
}
