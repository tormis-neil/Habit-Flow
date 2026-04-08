package com.habitflow.app.domain.util

import com.habitflow.app.domain.model.HabitFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    @Test
    fun `empty list returns zero streaks`() {
        val result = StreakCalculator.calculate(emptyList())
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.longestStreak)
    }

    @Test
    fun `single completion today returns streak of 1`() {
        val dates = listOf(LocalDate.now())
        val result = StreakCalculator.calculate(dates)
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    @Test
    fun `consecutive days ending today`() {
        val today = LocalDate.now()
        val dates = (0L..4L).map { today.minusDays(it) } // 5 consecutive days
        val result = StreakCalculator.calculate(dates)
        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `gap in streak resets current but preserves longest`() {
        val today = LocalDate.now()
        // 3 days ago through today = 4 day streak
        // Then a gap, then 6 days ago through 5 days ago = 2 day streak
        val dates = listOf(
            today,
            today.minusDays(1),
            today.minusDays(2),
            today.minusDays(3),
            // gap at day 4
            today.minusDays(5),
            today.minusDays(6),
        )
        val result = StreakCalculator.calculate(dates)
        assertEquals(4, result.currentStreak)
        assertEquals(4, result.longestStreak)
    }

    @Test
    fun `old streak no longer active`() {
        val today = LocalDate.now()
        // Streak ended 5 days ago, not touching today or yesterday
        val dates = listOf(
            today.minusDays(5),
            today.minusDays(6),
            today.minusDays(7),
        )
        val result = StreakCalculator.calculate(dates)
        assertEquals(0, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `streak ending yesterday is still active`() {
        val today = LocalDate.now()
        val dates = listOf(
            today.minusDays(1),
            today.minusDays(2),
            today.minusDays(3),
        )
        val result = StreakCalculator.calculate(dates)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `longer past streak is preserved as longest`() {
        val today = LocalDate.now()
        // Current: 2 days (today + yesterday)
        // Past: 5 consecutive days with a gap before current
        val dates = listOf(
            today,
            today.minusDays(1),
            // gap
            today.minusDays(10),
            today.minusDays(11),
            today.minusDays(12),
            today.minusDays(13),
            today.minusDays(14),
        )
        val result = StreakCalculator.calculate(dates)
        assertEquals(2, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `weekly frequency consecutive weeks`() {
        val today = LocalDate.now()
        val dates = listOf(
            today,
            today.minusWeeks(1),
            today.minusWeeks(2),
        )
        val result = StreakCalculator.calculate(dates, HabitFrequency.WEEKLY)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `duplicate dates are deduplicated`() {
        val today = LocalDate.now()
        val dates = listOf(today, today, today.minusDays(1), today.minusDays(1))
        val result = StreakCalculator.calculate(dates)
        assertEquals(2, result.currentStreak)
        assertEquals(2, result.longestStreak)
    }
}
