package io.github.alight77.habittracker.feature.stats

import io.github.alight77.habittracker.data.local.RecordEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeeklyCheckInTrendPoint(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val observedThrough: LocalDate,
    val completedDays: Int,
    val notYetCreated: Boolean,
    val isCurrentWeek: Boolean
)

fun calculateWeeklyCheckInTrend(
    today: LocalDate,
    habitId: Int,
    createdDate: LocalDate,
    records: List<RecordEntity>
): List<WeeklyCheckInTrendPoint> {
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeekStart = currentWeekStart.minusWeeks(4)
    val eligibleStart = maxOf(firstWeekStart, createdDate).toEpochDay()
    val todayEpochDay = today.toEpochDay()

    // 撤销状态不计数；日期去重也保护纯函数免受重复输入影响。
    val doneDays = records.asSequence()
        .filter { record ->
            record.habitId == habitId && record.isDone && record.date in eligibleStart..todayEpochDay
        }
        .map { record -> record.date }
        .toSet()

    return (4 downTo 0).map { weeksAgo ->
        val weekStart = currentWeekStart.minusWeeks(weeksAgo.toLong())
        val weekEnd = weekStart.plusDays(6)
        val isCurrentWeek = weeksAgo == 0
        val observedThrough = if (isCurrentWeek) today else weekEnd
        val notYetCreated = createdDate.isAfter(observedThrough)
        // 创建前整周保留槽位，但零值只是占位，不代表已创建后零打卡。
        val completedDays = if (notYetCreated) {
            0
        } else {
            (maxOf(weekStart, createdDate).toEpochDay()..observedThrough.toEpochDay())
                .count { day -> day in doneDays }
        }

        WeeklyCheckInTrendPoint(
            weekStart = weekStart,
            weekEnd = weekEnd,
            observedThrough = observedThrough,
            completedDays = completedDays,
            notYetCreated = notYetCreated,
            isCurrentWeek = isCurrentWeek
        )
    }
}
