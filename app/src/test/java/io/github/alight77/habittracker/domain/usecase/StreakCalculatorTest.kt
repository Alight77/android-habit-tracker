package io.github.alight77.habittracker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    @Test
    fun calculateStreak_returnsZeroWhenThereAreNoDoneDates() {
        val today = LocalDate.of(2026, 7, 7)

        val streak = calculateStreak(emptyList(), today)

        assertEquals(0, streak)
    }

    @Test
    fun calculateStreak_countsConsecutiveDatesEndingToday() {
        val today = LocalDate.of(2026, 7, 7)
        val dates = listOf(
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 5)
        )

        val streak = calculateStreak(dates, today)

        assertEquals(3, streak)
    }

    @Test
    fun calculateStreak_stopsAtFirstMissingDate() {
        val today = LocalDate.of(2026, 7, 7)
        val dates = listOf(
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 5),
            LocalDate.of(2026, 7, 4)
        )

        val streak = calculateStreak(dates, today)

        assertEquals(1, streak)
    }

    @Test
    fun calculateStreak_ignoresDuplicateDoneDates() {
        val today = LocalDate.of(2026, 7, 7)
        val dates = listOf(
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 6)
        )

        val streak = calculateStreak(dates, today)

        assertEquals(2, streak)
    }

    @Test
    fun calculateStreak_returnsZeroWhenTodayIsNotDone() {
        val today = LocalDate.of(2026, 7, 7)
        val dates = listOf(
            LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 5)
        )

        val streak = calculateStreak(dates, today)

        assertEquals(0, streak)
    }

    @Test
    fun calculateLongestStreak_returnsZeroForEmptyDates() {
        assertEquals(0, calculateLongestStreak(emptyList()))
    }

    @Test
    fun calculateLongestStreak_usesLongestPastSegmentWithoutToday() {
        val dates = listOf(
            LocalDate.of(2026, 7, 2),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 7, 6)
        )

        assertEquals(3, calculateLongestStreak(dates))
    }

    @Test
    fun calculateLongestStreak_ignoresDuplicateDatesAndInputOrder() {
        val dates = listOf(
            LocalDate.of(2026, 7, 5),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 7, 1)
        )

        assertEquals(3, calculateLongestStreak(dates))
    }
}
