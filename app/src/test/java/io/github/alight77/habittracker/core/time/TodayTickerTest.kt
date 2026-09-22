package io.github.alight77.habittracker.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TodayTickerTest {

    @Test
    fun millisUntilNextDay_returnsMillisUntilNextLocalMidnight() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2026-07-07T23:30:00Z")

        val millis = millisUntilNextDay(now, zoneId)

        assertEquals(30L * 60L * 1000L, millis)
    }

    @Test
    fun millisUntilNextDay_returnsZeroWhenNowIsPastComputedMidnightBoundary() {
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2026-07-07T00:00:00Z")

        val millis = millisUntilNextDay(now, zoneId)

        assertEquals(24L * 60L * 60L * 1000L, millis)
    }
}
