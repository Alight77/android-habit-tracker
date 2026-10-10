package io.github.alight77.habittracker.core.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodayTickerTest {

    @Test
    fun systemTodayFlow_updatesAtLocalMidnight() = runTest {
        val start = Instant.parse("2026-10-10T23:59:00Z")
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch {
            systemTodayFlow(ZoneId.of("UTC"), now = {
                start.plusMillis(testScheduler.currentTime)
            }).collect { dates += it }
        }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), dates)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)), dates)
    }

    @Test
    fun systemTodayFlow_reschedulesAfterClockMovesForwardAndBackward() = runTest {
        var now = Instant.parse("2026-10-10T08:00:00Z")
        val refreshes = MutableSharedFlow<Unit>()
        val dates = mutableListOf<LocalDate>()
        backgroundScope.launch {
            systemTodayFlow(ZoneId.of("UTC"), refreshes, now = { now })
                .collect { dates += it }
        }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), dates)

        now = Instant.parse("2026-10-11T08:00:00Z")
        refreshes.emit(Unit)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 11), dates.last())

        now = Instant.parse("2026-10-09T08:00:00Z")
        refreshes.emit(Unit)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 9), dates.last())
    }

    @Test
    fun millisUntilNextDay_returnsMillisUntilNextLocalMidnight() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2026-07-07T23:30:00Z")

        val millis = millisUntilNextDay(now, zoneId)

        assertEquals(30L * 60L * 1000L, millis)
    }

    @Test
    fun millisUntilNextDay_accountsForDaylightSavingTransitions() {
        val newYork = ZoneId.of("America/New_York")

        assertEquals(
            23L * 60L * 60L * 1000L,
            millisUntilNextDay(Instant.parse("2026-03-08T05:00:00Z"), newYork)
        )
        assertEquals(
            25L * 60L * 60L * 1000L,
            millisUntilNextDay(Instant.parse("2026-11-01T04:00:00Z"), newYork)
        )
    }

    @Test
    fun millisUntilNextDay_returnsZeroWhenNowIsPastComputedMidnightBoundary() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2026-07-07T00:00:00Z")

        val millis = millisUntilNextDay(now, zoneId)

        assertEquals(24L * 60L * 60L * 1000L, millis)
    }
}
