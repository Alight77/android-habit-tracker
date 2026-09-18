package com.example.habittracker.domain.usecase

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
}