package io.github.alight77.habittracker.domain.usecase

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class RecentGoalProgress(
    val completed: Int,
    val target: Int
)

fun calculateRecentGoalProgress(
    targetPerWeek: Int,
    createdDate: LocalDate,
    doneDates: List<LocalDate>,
    today: LocalDate
): RecentGoalProgress {
    val eligibleStart = maxOf(today.minusDays(6), createdDate)
    if (eligibleStart.isAfter(today)) return RecentGoalProgress(0, 0)

    val eligibleDays = ChronoUnit.DAYS.between(eligibleStart, today).toInt() + 1
    val effectiveTarget = targetPerWeek.coerceAtLeast(0).coerceAtMost(eligibleDays)
    val completed = doneDates.asSequence()
        .filter { date -> !date.isBefore(eligibleStart) && !date.isAfter(today) }
        .distinct()
        .count()
        .coerceAtMost(effectiveTarget)

    return RecentGoalProgress(completed, effectiveTarget)
}
