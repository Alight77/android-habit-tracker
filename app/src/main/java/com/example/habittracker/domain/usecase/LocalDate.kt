package com.example.habittracker.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun epochMillisToLocalDate(timestamp: Long): LocalDate{
    val zoneId = ZoneId.systemDefault()
    val dates = Instant.ofEpochMilli(timestamp)
            .atZone(zoneId)
            .toLocalDate()
    return dates
}
