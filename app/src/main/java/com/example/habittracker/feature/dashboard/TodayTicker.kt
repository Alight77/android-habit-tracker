package com.example.habittracker.feature.dashboard

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun systemTodayFlow(zoneId: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> {
    return flow {
        var lastEmitted: LocalDate? = null

        while (currentCoroutineContext().isActive) {
            val today = LocalDate.now(zoneId)
            if (today != lastEmitted) {
                emit(today)
                lastEmitted = today
            }

            delay(millisUntilNextDay(Instant.now(), zoneId).coerceAtLeast(1L))
        }
    }
}

internal fun millisUntilNextDay(now: Instant, zoneId: ZoneId): Long {
    val today = now.atZone(zoneId).toLocalDate()
    val nextMidnight = today
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()

    return Duration.between(now, nextMidnight)
        .toMillis()
        .coerceAtLeast(0L)
}