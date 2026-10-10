package io.github.alight77.habittracker.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import java.time.LocalDate

/** 系统变更广播与页面恢复共用的日期刷新入口；接收器只在 dates 被收集时注册。 */
class TodayDateSource(context: Context) {
    private val applicationContext = context.applicationContext
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val dates: Flow<LocalDate> = systemTodayFlow(
        refreshes = merge(refreshes, systemClockChanges(applicationContext))
    )

    fun today(): LocalDate = LocalDate.now()

    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private fun systemClockChanges(context: Context): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // 动态注册随 Flow 订阅而生灭，不在 Manifest 中长期监听系统时间变化。
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
