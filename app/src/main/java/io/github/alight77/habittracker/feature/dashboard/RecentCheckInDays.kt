package io.github.alight77.habittracker.feature.dashboard

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class RecentDayStatus {
    BEFORE_CREATION,
    NOT_CHECKED,
    CHECKED
}

@Immutable
data class RecentCheckInDay(
    val date: LocalDate,
    val status: RecentDayStatus
)

internal fun recentCheckInDays(
    createdDate: LocalDate,
    doneDates: List<LocalDate>,
    today: LocalDate
): List<RecentCheckInDay> {
    val doneDateSet = doneDates.toSet()
    // 七格始终对应最近七个自然日；创建前的日期不能被误认为漏打。
    return (6L downTo 0L).map { daysAgo ->
        val date = today.minusDays(daysAgo)
        val status = when {
            date.isBefore(createdDate) -> RecentDayStatus.BEFORE_CREATION
            date in doneDateSet -> RecentDayStatus.CHECKED
            else -> RecentDayStatus.NOT_CHECKED
        }
        RecentCheckInDay(date, status)
    }
}
