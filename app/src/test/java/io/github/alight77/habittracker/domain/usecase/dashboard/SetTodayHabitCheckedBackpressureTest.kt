package io.github.alight77.habittracker.domain.usecase.dashboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 慢订阅者用可释放门闩模拟，所有等待均有超时，避免回归测试永久挂起。 */
class SetTodayHabitCheckedBackpressureTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun fullEventBufferDoesNotBlockOtherHabitAndPreservesEventOrder() = runBlocking {
        val releaseSubscriber = CompletableDeferred<Unit>()
        val otherHabitPersisted = CompletableDeferred<Unit>()
        val received = mutableListOf<SetTodayHabitCheckedUseCase.DomainEvent>()
        val useCase = newUseCase { habitId ->
            if (habitId == 34) otherHabitPersisted.complete(Unit)
        }
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            useCase.events.collect { event ->
                received += event
                if (received.size == 1) releaseSubscriber.await()
            }
        }
        try {
            // 第一个事件被订阅者取走，随后 64 个事件填满 SharedFlow 缓冲区。
            for (habitId in 1..32) {
                withTimeout(2_000L) { useCase(command(habitId)) }
            }
            val blockedHabit = async(start = CoroutineStart.UNDISPATCHED) {
                useCase(command(33))
            }
            val otherHabit = async(start = CoroutineStart.UNDISPATCHED) {
                useCase(command(34))
            }

            // 不同习惯的数据库写入不应被慢事件订阅者挡住。
            withTimeout(1_000L) { otherHabitPersisted.await() }
            withTimeout(1_000L) { blockedHabit.await(); otherHabit.await() }

            releaseSubscriber.complete(Unit)
            withTimeout(2_000L) {
                while (received.size < 68) kotlinx.coroutines.yield()
            }
            val expected = (1..34).flatMap { habitId ->
                listOf(
                    SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(habitId, true, today),
                    SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(habitId, true, today)
                )
            }
            assertEquals(expected, received)
        } finally {
            releaseSubscriber.complete(Unit)
            withTimeout(2_000L) { collector.cancelAndJoin() }
        }
    }

    @Test
    fun cancelledCallerCanExitWhenSubscriberIsStalled() = runBlocking {
        val releaseSubscriber = CompletableDeferred<Unit>()
        val useCase = newUseCase()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            useCase.events.collect { releaseSubscriber.await() }
        }
        try {
            for (habitId in 1..32) {
                withTimeout(2_000L) { useCase(command(habitId)) }
            }
            val caller = async(start = CoroutineStart.UNDISPATCHED) { useCase(command(33)) }
            caller.cancel()
            // 原调用方的取消不应被事件投递的 NonCancellable 区域无限延迟。
            withTimeout(1_000L) { caller.join() }
        } finally {
            releaseSubscriber.complete(Unit)
            withTimeout(2_000L) { collector.cancelAndJoin() }
        }
    }

    @Test
    fun cancellingSlowSubscriberReleasesEventDeliveryAndFutureCommands() = runBlocking {
        val releaseSubscriber = CompletableDeferred<Unit>()
        val useCase = newUseCase()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            useCase.events.collect { releaseSubscriber.await() }
        }
        try {
            for (habitId in 1..32) {
                withTimeout(2_000L) { useCase(command(habitId)) }
            }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { useCase(command(33)) }
            collector.cancelAndJoin()
            withTimeout(2_000L) {
                pending.await()
                useCase(command(34))
            }
        } finally {
            releaseSubscriber.complete(Unit)
            withTimeout(2_000L) { collector.cancelAndJoin() }
        }
    }

    private fun newUseCase(onPersist: (Int) -> Unit = {}): SetTodayHabitCheckedUseCase =
        SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                onPersist(command.habitId)
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(true) },
            dispatcher = Dispatchers.Unconfined
        )

    private fun command(habitId: Int) =
        SetTodayHabitCheckedUseCase.Command(habitId, true, today)
}
