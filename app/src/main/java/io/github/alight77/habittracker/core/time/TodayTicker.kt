package io.github.alight77.habittracker.core.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
internal fun systemTodayFlow(
    zoneId: ZoneId? = null,
    refreshes: Flow<Unit> = emptyFlow(),
    now: () -> Instant = Instant::now
): Flow<LocalDate> {
    // 首次订阅及外部刷新都重新启动午夜计时，避免继续等待旧时区/旧系统时间算出的午夜。
    return refreshes.onStart { emit(Unit) }.flatMapLatest {
        flow {
            var lastEmitted: LocalDate? = null

            while (currentCoroutineContext().isActive) {
                // 默认时区可能在 Flow 存活期间改变，不能在创建 Flow 时固定下来。
                val currentZone = zoneId ?: ZoneId.systemDefault()
                val instant = now()
                val today = instant.atZone(currentZone).toLocalDate()
                if (today != lastEmitted) {
                    emit(today)
                    lastEmitted = today
                }

                // emit 可能挂起；排下一次延迟前重读时钟，避免跨日时漏发新日期。
                val nextZone = zoneId ?: ZoneId.systemDefault()
                val nextInstant = now()
                if (nextInstant.atZone(nextZone).toLocalDate() != today) continue
                delay(millisUntilNextDay(nextInstant, nextZone).coerceAtLeast(1L))
            }
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
