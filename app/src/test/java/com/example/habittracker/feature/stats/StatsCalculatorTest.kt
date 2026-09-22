package com.example.habittracker.feature.stats

import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsCalculatorTest {

    @Test
    fun calculateStats_returnsZeroSummaryForEmptyData() {
        val today = LocalDate.of(2026, 7, 7)

        val summary = calculateStats(
            habits = emptyList(),
            records = emptyList(),
            today = today
        )

        assertEquals(
            StatsSummary(
                totalHabits = 0,
                todayDoneCount = 0,
                totalDoneCount = 0,
                recentSevenDayGoalPercent = 0,
                longestStreak = 0
            ),
            summary
        )
    }

    @Test
    fun calculateStats_summarizesSingleHabitRecords() {
        val today = LocalDate.of(2026, 7, 7)
        val habits = listOf(habit(id = 1))
        val records = listOf(
            doneRecord(habitId = 1, date = today),
            doneRecord(habitId = 1, date = today.minusDays(1)),
            doneRecord(habitId = 1, date = today.minusDays(8))
        )

        val summary = calculateStats(habits, records, today)

        assertEquals(
            StatsSummary(
                totalHabits = 1,
                todayDoneCount = 1,
                totalDoneCount = 3,
                recentSevenDayGoalPercent = 29,
                longestStreak = 2
            ),
            summary
        )
    }

    @Test
    fun calculateStats_summarizesMultipleHabits() {
        val today = LocalDate.of(2026, 7, 7)
        val habits = listOf(
            habit(id = 1),
            habit(id = 2)
        )
        val records = listOf(
            doneRecord(habitId = 1, date = today),
            doneRecord(habitId = 1, date = today.minusDays(1)),
            doneRecord(habitId = 2, date = today),
            skippedRecord(habitId = 2, date = today.minusDays(1))
        )

        val summary = calculateStats(habits, records, today)

        assertEquals(
            StatsSummary(
                totalHabits = 2,
                todayDoneCount = 2,
                totalDoneCount = 3,
                recentSevenDayGoalPercent = 21,
                longestStreak = 2
            ),
            summary
        )
    }

    @Test
    fun calculateStats_keepsLongestHistoricalStreakWhenTodayIsNotDone() {
        val today = LocalDate.of(2026, 7, 7)
        val habits = listOf(habit(id = 1))
        val records = listOf(
            doneRecord(habitId = 1, date = today.minusDays(2)),
            doneRecord(habitId = 1, date = today.minusDays(3)),
            doneRecord(habitId = 1, date = today.minusDays(4))
        )

        val summary = calculateStats(habits, records, today)

        assertEquals(3, summary.longestStreak)
    }

    @Test
    fun calculateStats_usesConfiguredWeeklyGoalForSevenDayRate() {
        val today = LocalDate.of(2026, 7, 7)
        val summary = calculateStats(
            habits = listOf(habit(id = 1, targetPerWeek = 3)),
            records = listOf(
                doneRecord(1, today),
                doneRecord(1, today.minusDays(1)),
                doneRecord(1, today.minusDays(2))
            ),
            today = today
        )

        assertEquals(100, summary.recentSevenDayGoalPercent)
    }

    @Test
    fun calculateStats_capsEachHabitBeforeAggregatingGoals() {
        val today = LocalDate.of(2026, 7, 7)
        val summary = calculateStats(
            habits = listOf(habit(id = 1, targetPerWeek = 1), habit(id = 2, targetPerWeek = 3)),
            records = (0L..4L).map { offset ->
                doneRecord(1, today.minusDays(offset))
            },
            today = today
        )

        assertEquals(25, summary.recentSevenDayGoalPercent)
    }

    @Test
    fun calculateStats_proratesGoalFromCreationDay() {
        val today = LocalDate.of(2026, 7, 7)
        val createdAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val summary = calculateStats(
            habits = listOf(habit(id = 1, targetPerWeek = 3, createdAt = createdAt)),
            records = listOf(doneRecord(1, today)),
            today = today
        )

        assertEquals(100, summary.recentSevenDayGoalPercent)
    }

    private fun habit(id: Int, targetPerWeek: Int = 7, createdAt: Long = 0L): HabitEntity {
        return HabitEntity(
            id = id,
            name = "Habit $id",
            description = "",
            targetPerWeek = targetPerWeek,
            createdAt = createdAt
        )
    }

    private fun doneRecord(habitId: Int, date: LocalDate): RecordEntity {
        return RecordEntity(
            habitId = habitId,
            date = date.toEpochDay(),
            isDone = true
        )
    }

    private fun skippedRecord(habitId: Int, date: LocalDate): RecordEntity {
        return RecordEntity(
            habitId = habitId,
            date = date.toEpochDay(),
            isDone = false
        )
    }
}
