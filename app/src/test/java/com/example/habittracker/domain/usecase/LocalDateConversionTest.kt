package com.example.habittracker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LocalDateConversionTest {

    @Test
    fun epochDayToLocalDate_returnsSameDateWithoutUsingSystemZone() {
        val date = LocalDate.of(2026, 7, 7)

        assertEquals(date, epochDayToLocalDate(date.toEpochDay()))
    }
}
