package io.github.alight77.habittracker.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecentCheckInDaysTest {

    @Test
    fun fixedSevenDaysKeepEarlierChecksWhenYesterdayWasMissed() {
        val today = LocalDate.of(2026, 10, 11)
        val days = recentCheckInDays(
            createdDate = today.minusDays(20),
            doneDates = listOf(today.minusDays(3), today.minusDays(2), today.minusDays(2)),
            today = today
        )

        assertEquals((6L downTo 0L).map(today::minusDays), days.map { it.date })
        assertEquals(
            listOf(
                RecentDayStatus.NOT_CHECKED,
                RecentDayStatus.NOT_CHECKED,
                RecentDayStatus.NOT_CHECKED,
                RecentDayStatus.CHECKED,
                RecentDayStatus.CHECKED,
                RecentDayStatus.NOT_CHECKED,
                RecentDayStatus.NOT_CHECKED
            ),
            days.map { it.status }
        )
    }

    @Test
    fun newHabitDoesNotTreatDatesBeforeCreationAsMissed() {
        val today = LocalDate.of(2026, 10, 11)
        val days = recentCheckInDays(
            createdDate = today,
            doneDates = listOf(today, today.minusDays(1)),
            today = today
        )

        assertEquals(List(6) { RecentDayStatus.BEFORE_CREATION } + RecentDayStatus.CHECKED,
            days.map { it.status })
    }

    @Test
    fun tomorrowMovesWindowWithoutErasingPreviousChecks() {
        val today = LocalDate.of(2026, 10, 11)
        val doneDates = listOf(today.minusDays(6), today.minusDays(1))
        val tomorrow = recentCheckInDays(
            createdDate = today.minusDays(20),
            doneDates = doneDates,
            today = today.plusDays(1)
        )

        assertEquals(today.minusDays(5), tomorrow.first().date)
        assertEquals(RecentDayStatus.CHECKED, tomorrow[4].status)
        assertEquals(RecentDayStatus.NOT_CHECKED, tomorrow.last().status)
    }
}
