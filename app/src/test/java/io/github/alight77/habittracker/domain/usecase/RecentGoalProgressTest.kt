package io.github.alight77.habittracker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecentGoalProgressTest {

    private val today = LocalDate.of(2026, 7, 7)

    @Test
    fun usesConfiguredGoalAfterFullSevenDays() {
        val progress = calculateRecentGoalProgress(
            targetPerWeek = 3,
            createdDate = today.minusDays(10),
            doneDates = listOf(today, today.minusDays(1), today.minusDays(2)),
            today = today
        )

        assertEquals(RecentGoalProgress(completed = 3, target = 3), progress)
    }

    @Test
    fun proratesGoalOnCreationDay() {
        val progress = calculateRecentGoalProgress(
            targetPerWeek = 3,
            createdDate = today,
            doneDates = listOf(today),
            today = today
        )

        assertEquals(RecentGoalProgress(completed = 1, target = 1), progress)
    }

    @Test
    fun increasesEffectiveGoalOnTheNextDay() {
        val progress = calculateRecentGoalProgress(
            targetPerWeek = 3,
            createdDate = today.minusDays(1),
            doneDates = listOf(today.minusDays(1)),
            today = today
        )

        assertEquals(RecentGoalProgress(completed = 1, target = 2), progress)
    }

    @Test
    fun capsExtraCompletionsAtTheEffectiveGoal() {
        val progress = calculateRecentGoalProgress(
            targetPerWeek = 1,
            createdDate = today.minusDays(10),
            doneDates = (0L..4L).map(today::minusDays),
            today = today
        )

        assertEquals(RecentGoalProgress(completed = 1, target = 1), progress)
    }

    @Test
    fun ignoresDuplicateAndOutOfWindowOrPreCreationDates() {
        val progress = calculateRecentGoalProgress(
            targetPerWeek = 5,
            createdDate = today.minusDays(2),
            doneDates = listOf(
                today.minusDays(2),
                today.minusDays(2),
                today.minusDays(3),
                today.plusDays(1)
            ),
            today = today
        )

        assertEquals(RecentGoalProgress(completed = 1, target = 3), progress)
    }

    @Test
    fun returnsZeroWhenNoEligibleDaysExist() {
        val progress = calculateRecentGoalProgress(
            targetPerWeek = 3,
            createdDate = today.plusDays(1),
            doneDates = emptyList(),
            today = today
        )

        assertEquals(RecentGoalProgress(completed = 0, target = 0), progress)
    }
}
