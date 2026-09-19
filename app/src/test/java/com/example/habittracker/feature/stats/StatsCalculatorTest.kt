package com.example.habittracker.feature.stats

import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
                recentSevenDayCompletionPercent = 0,
                bestCurrentStreak = 0
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
                recentSevenDayCompletionPercent = 29,
                bestCurrentStreak = 2
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
                recentSevenDayCompletionPercent = 21,
                bestCurrentStreak = 2
            ),
            summary
        )
    }

    private fun habit(id: Int): HabitEntity {
        return HabitEntity(
            id = id,
            name = "Habit $id",
            description = "",
            targetPerWeek = 7,
            createdAt = 0L
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
