package io.github.alight77.habittracker.feature.stats

import io.github.alight77.habittracker.data.local.RecordEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyCheckInTrendTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val oldCreationDate = LocalDate.of(2020, 1, 1)

    @Test
    fun returnsFiveConsecutiveWeeksInChronologicalOrder() {
        val points = trend(
            records = listOf(
                done(LocalDate.of(2026, 10, 5)),
                done(LocalDate.of(2026, 10, 12)),
                done(LocalDate.of(2026, 10, 18)),
                done(LocalDate.of(2026, 10, 19)),
                done(LocalDate.of(2026, 10, 20)),
                done(LocalDate.of(2026, 10, 25)),
                done(today)
            )
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 11, 2)
            ),
            points.map { it.weekStart }
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 18),
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8)
            ),
            points.map { it.weekEnd }
        )
        assertEquals(listOf(1, 2, 3, 0, 1), points.map { it.completedDays })
    }

    @Test
    fun mondayStartsTheCurrentWeek() {
        val monday = LocalDate.of(2026, 11, 2)
        val points = trend(
            today = monday,
            records = listOf(done(monday.minusDays(1)), done(monday))
        )

        assertEquals(1, points[3].completedDays)
        assertEquals(monday, points.last().weekStart)
        assertEquals(monday, points.last().observedThrough)
        assertEquals(1, points.last().completedDays)
    }

    @Test
    fun sundayRemainsInTheCurrentWeek() {
        val sunday = LocalDate.of(2026, 11, 8)
        val point = trend(today = sunday, records = listOf(done(sunday))).last()

        assertEquals(LocalDate.of(2026, 11, 2), point.weekStart)
        assertEquals(sunday, point.weekEnd)
        assertEquals(sunday, point.observedThrough)
        assertEquals(1, point.completedDays)
    }

    @Test
    fun creationMidweekExcludesEarlierDates() {
        val thursday = LocalDate.of(2026, 11, 5)
        val friday = thursday.plusDays(1)
        val point = trend(
            today = friday,
            createdDate = thursday,
            records = listOf(done(thursday.minusDays(3)), done(thursday), done(friday))
        ).last()

        assertEquals(2, point.completedDays)
        assertFalse(point.notYetCreated)
    }

    @Test
    fun wholeWeeksBeforeCreationAreMarkedNotApplicable() {
        val points = trend(createdDate = LocalDate.of(2026, 10, 20))

        assertEquals(listOf(true, true, false, false, false), points.map { it.notYetCreated })
        assertEquals(listOf(0, 0), points.take(2).map { it.completedDays })
    }

    @Test
    fun zeroCheckInsAfterCreationRemainApplicable() {
        val points = trend()

        assertTrue(points.all { it.completedDays == 0 })
        assertTrue(points.none { it.notYetCreated })
    }

    @Test
    fun uncheckedRecordDoesNotCountAsCompleted() {
        val point = trend(
            records = listOf(done(today.minusDays(1)), record(today, isDone = false))
        ).last()

        assertEquals(1, point.completedDays)
    }

    @Test
    fun duplicateCompletedRecordsCountOnlyOncePerDate() {
        val point = trend(records = listOf(done(today), done(today))).last()

        assertEquals(1, point.completedDays)
    }

    @Test
    fun otherHabitRecordsDoNotAffectSelection() {
        val point = trend(records = listOf(done(today, habitId = 8))).last()

        assertEquals(0, point.completedDays)
    }

    @Test
    fun futureRecordsDoNotCountInCurrentWeek() {
        val point = trend(records = listOf(done(today.plusDays(1)))).last()

        assertEquals(0, point.completedDays)
    }

    @Test
    fun recordsBeforeFiveWeekWindowDoNotCount() {
        val points = trend(records = listOf(done(LocalDate.of(2026, 10, 4))))

        assertTrue(points.all { it.completedDays == 0 })
    }

    @Test
    fun weekCanCrossCalendarYear() {
        val firstDayOfYear = LocalDate.of(2027, 1, 1)
        val point = trend(
            today = firstDayOfYear,
            records = listOf(
                done(LocalDate.of(2026, 12, 31)),
                done(firstDayOfYear),
                done(firstDayOfYear.plusDays(1))
            )
        ).last()

        assertEquals(LocalDate.of(2026, 12, 28), point.weekStart)
        assertEquals(LocalDate.of(2027, 1, 3), point.weekEnd)
        assertEquals(firstDayOfYear, point.observedThrough)
        assertEquals(2, point.completedDays)
    }

    @Test
    fun leapDayBelongsToItsNaturalWeek() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val friday = leapDay.plusDays(1)
        val point = trend(
            today = friday,
            records = listOf(done(leapDay), done(friday))
        ).last()

        assertEquals(LocalDate.of(2024, 2, 26), point.weekStart)
        assertEquals(LocalDate.of(2024, 3, 3), point.weekEnd)
        assertEquals(2, point.completedDays)
    }

    @Test
    fun onlyCurrentWeekIsObservedThroughToday() {
        val points = trend()

        assertEquals(points.take(4).map { it.weekEnd }, points.take(4).map { it.observedThrough })
        assertEquals(today, points.last().observedThrough)
        assertEquals(listOf(false, false, false, false, true), points.map { it.isCurrentWeek })
    }

    @Test
    fun sevenDistinctCompletedDaysAreNotCappedByWeeklyTarget() {
        val sunday = LocalDate.of(2026, 11, 8)
        val completedWeek = (0L..6L).map { offset ->
            done(LocalDate.of(2026, 11, 2).plusDays(offset))
        }
        val point = trend(today = sunday, records = completedWeek).last()

        assertEquals(7, point.completedDays)
    }

    private fun trend(
        today: LocalDate = this.today,
        createdDate: LocalDate = oldCreationDate,
        records: List<RecordEntity> = emptyList()
    ): List<WeeklyCheckInTrendPoint> = calculateWeeklyCheckInTrend(
        today = today,
        habitId = 7,
        createdDate = createdDate,
        records = records
    )

    private fun done(date: LocalDate, habitId: Int = 7): RecordEntity =
        record(date = date, habitId = habitId, isDone = true)

    private fun record(date: LocalDate, habitId: Int = 7, isDone: Boolean): RecordEntity =
        RecordEntity(habitId = habitId, date = date.toEpochDay(), isDone = isDone)
}
