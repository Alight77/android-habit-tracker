package com.example.habittracker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LocalDateConversionTest {

    @Test
    fun epochMillisToLocalDate_returnsDateForStartOfDayInSystemZone() {
        val zoneId = ZoneId.systemDefault()
        val date = LocalDate.of(2026, 7, 7)
        val millis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val result = epochMillisToLocalDate(millis)

        assertEquals(date, result)
    }

    @Test
    fun epochMillisToLocalDate_returnsDateForMiddayInSystemZone() {
        val zoneId = ZoneId.systemDefault()
        val date = LocalDate.of(2026, 7, 7)
        val millis = date.atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()

        val result = epochMillisToLocalDate(millis)

        assertEquals(date, result)
    }
}