package io.github.alight77.habittracker.feature.stats

import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.domain.usecase.calculateLongestStreak
import io.github.alight77.habittracker.domain.usecase.calculateRecentGoalProgress
import io.github.alight77.habittracker.domain.usecase.epochDayToLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class StatsSummary(
    val totalHabits: Int,
    val todayDoneCount: Int,
    val totalDoneCount: Int,
    val recentSevenDayGoalPercent: Int,
    val longestStreak: Int
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
        record to epochDayToLocalDate(record.date)
    }

    val todayDoneCount = doneRecordsWithDates
        .filter { (_, date) -> date == today }
        .map { (record, _) -> record.habitId }
        .distinct()
        .count()

    val recordsByHabit = doneRecordsWithDates.groupBy(
        keySelector = { (record, _) -> record.habitId },
        valueTransform = { (_, date) -> date }
    )
    val zoneId = ZoneId.systemDefault()
    val goalProgress = habits.map { habit ->
        calculateRecentGoalProgress(
            targetPerWeek = habit.targetPerWeek,
            createdDate = Instant.ofEpochMilli(habit.createdAt)
                .atZone(zoneId)
                .toLocalDate(),
            doneDates = recordsByHabit[habit.id].orEmpty(),
            today = today
        )
    }
    val totalGoal = goalProgress.sumOf { it.target }
    val recentSevenDayGoalPercent = if (totalGoal == 0) {
        0
    } else {
        (goalProgress.sumOf { it.completed }.toDouble() / totalGoal * 100).roundToInt()
    }
    val longestStreak = habits.maxOfOrNull { habit ->
        calculateLongestStreak(recordsByHabit[habit.id].orEmpty())
    } ?: 0

    return StatsSummary(
        totalHabits = habits.size,
        todayDoneCount = todayDoneCount,
        totalDoneCount = doneRecords.size,
        recentSevenDayGoalPercent = recentSevenDayGoalPercent,
        longestStreak = longestStreak
    )
}
