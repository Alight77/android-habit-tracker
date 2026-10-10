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
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
