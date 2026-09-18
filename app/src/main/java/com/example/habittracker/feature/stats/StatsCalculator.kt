package com.example.habittracker.feature.stats

import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.domain.usecase.calculateStreak
import com.example.habittracker.domain.usecase.epochMillisToLocalDate
import java.time.LocalDate
import kotlin.math.roundToInt

data class StatsSummary(
    val totalHabits: Int,
    val todayDoneCount: Int,
    val totalDoneCount: Int,
    val recentSevenDayCompletionPercent: Int,
    val bestCurrentStreak: Int
)

fun calculateStats(
    habits: List<HabitEntity>,
    records: List<RecordEntity>,
    today: LocalDate
): StatsSummary {
    val habitIds = habits.map { it.id }.toSet()
    val doneRecords = records.filter { record ->
        record.isDone && record.habitId in habitIds
    }
    val doneRecordsWithDates = doneRecords.map { record ->
        record to epochMillisToLocalDate(record.date)
    }

    val todayDoneCount = doneRecordsWithDates
        .filter { (_, date) -> date == today }
        .map { (record, _) -> record.habitId }
        .distinct()
        .count()

    val windowStart = today.minusDays(6)
    val recentDoneHabitDays = doneRecordsWithDates
        .filter { (_, date) -> !date.isBefore(windowStart) && !date.isAfter(today) }
        .map { (record, date) -> record.habitId to date }
        .distinct()
        .count()

    val recentSevenDayCompletionPercent = if (habits.isEmpty()) {
        0
    } else {
        ((recentDoneHabitDays.toDouble() / (habits.size * 7).toDouble()) * 100).roundToInt()
    }

    val recordsByHabit = doneRecordsWithDates.groupBy(
        keySelector = { (record, _) -> record.habitId },
        valueTransform = { (_, date) -> date }
    )
    val bestCurrentStreak = habits.maxOfOrNull { habit ->
        calculateStreak(recordsByHabit[habit.id].orEmpty(), today)
    } ?: 0

    return StatsSummary(
        totalHabits = habits.size,
        todayDoneCount = todayDoneCount,
        totalDoneCount = doneRecords.size,
        recentSevenDayCompletionPercent = recentSevenDayCompletionPercent,
        bestCurrentStreak = bestCurrentStreak
    )
}