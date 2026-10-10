package io.github.alight77.habittracker.domain.usecase.dashboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 用可控写入门闩固定危险时序；日期由 Command 指定，无需真的等待跨午夜。 */
class CrossMidnightThreeCommandRegressionTest {
    @Test
    fun threeCommandsAcrossMidnightPreserveBothDates() = runBlocking {
        val yesterday = LocalDate.of(2026, 9, 19)
        val today = yesterday.plusDays(1)
        val firstCommand = SetTodayHabitCheckedUseCase.Command(1, true, yesterday)
        val secondCommand = SetTodayHabitCheckedUseCase.Command(1, false, yesterday)
        val thirdCommand = SetTodayHabitCheckedUseCase.Command(1, true, today)
        val firstPersistStarted = CompletableDeferred<Unit>()
        val releaseFirstPersist = CompletableDeferred<Unit>()
        val applied = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val database = mutableMapOf<LocalDate, Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (command == firstCommand) {
                    firstPersistStarted.complete(Unit)
                    releaseFirstPersist.await()
                }
                applied += command
                database[command.today] = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                flowOf(database[command.today] == true)
            },
            dispatcher = Dispatchers.Unconfined
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(5).toList() }
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(firstCommand)
        }
        firstPersistStarted.await()
        useCase(secondCommand)
        useCase(thirdCommand)
        releaseFirstPersist.complete(Unit)
        first.await()
        val observedEvents = events.await()

        println("PR04 commands=$applied database=$database events=$observedEvents")
        assertEquals(
            "A cancellation for yesterday must not be replaced by a command for today",
            mapOf(yesterday to false, today to true),
            database
        )
        assertEquals(listOf(firstCommand, secondCommand, thirdCommand), applied)
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, yesterday),
                SetTodayHabitCheckedUseCase.DomainEvent.Superseded(1, true, false, yesterday),
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, today),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, false, yesterday),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, true, today)
            ),
            observedEvents
        )
    }

    @Test
    fun alternatingDatesKeepTheirOwnLatestTargets() = runBlocking {
        val dayA = LocalDate.of(2026, 9, 19)
        val dayB = dayA.plusDays(1)
        val first = SetTodayHabitCheckedUseCase.Command(1, true, dayA)
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val writes = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val database = mutableMapOf<LocalDate, Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (command == first && writes.isEmpty()) {
                    started.complete(Unit)
                    gate.await()
                }
                writes += command
                database[command.today] = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command -> flowOf(database[command.today] == true) },
            dispatcher = Dispatchers.Unconfined
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(6).toList() }
        }
        val processing = async(start = CoroutineStart.UNDISPATCHED) { useCase(first) }
        started.await()
        useCase(SetTodayHabitCheckedUseCase.Command(1, true, dayB))
        useCase(SetTodayHabitCheckedUseCase.Command(1, false, dayA))
        useCase(SetTodayHabitCheckedUseCase.Command(1, false, dayB))
        gate.complete(Unit)
        processing.await()

        assertEquals(mapOf(dayA to false, dayB to false), database)
        assertEquals(
            listOf(first, SetTodayHabitCheckedUseCase.Command(1, false, dayB),
                SetTodayHabitCheckedUseCase.Command(1, false, dayA)),
            writes
        )
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, dayA),
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, dayB),
                SetTodayHabitCheckedUseCase.DomainEvent.Superseded(1, true, false, dayA),
                SetTodayHabitCheckedUseCase.DomainEvent.Superseded(1, true, false, dayB),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, false, dayB),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, false, dayA)
            ),
            events.await()
        )
    }

    @Test
    fun failedOldDateDoesNotDiscardPendingNewDate() = runBlocking {
        val dayA = LocalDate.of(2026, 9, 19)
        val dayB = dayA.plusDays(1)
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val writes = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                writes += command
                if (command.today == dayA) {
                    started.complete(Unit)
                    gate.await()
                    error("database unavailable")
                }
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command -> flowOf(command.today == dayB) },
            dispatcher = Dispatchers.Unconfined
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(4).toList() }
        }
        val processing = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(SetTodayHabitCheckedUseCase.Command(1, true, dayA))
        }
        started.await()
        useCase(SetTodayHabitCheckedUseCase.Command(1, true, dayB))
        gate.complete(Unit)
        processing.await()

        assertEquals(listOf(dayA, dayB), writes.map { it.today })
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, dayA),
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, dayB),
                SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
                    1, true, dayA, SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, true, dayB)
            ),
            events.await()
        )
    }

    @Test
    fun sameDatePendingCommandsKeepOnlyTheLatestTarget() = runBlocking {
        val day = LocalDate.of(2026, 9, 19)
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (writes.isEmpty()) {
                    started.complete(Unit)
                    gate.await()
                }
                writes += command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command -> flowOf(command.targetChecked) },
            dispatcher = Dispatchers.Unconfined
        )
        val processing = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(SetTodayHabitCheckedUseCase.Command(1, true, day))
        }
        started.await()
        useCase(SetTodayHabitCheckedUseCase.Command(1, false, day))
        useCase(SetTodayHabitCheckedUseCase.Command(1, true, day))
        gate.complete(Unit)
        processing.await()

        assertEquals(listOf(true, true), writes)
    }

    @Test
    fun cancelledOldDateHandsPendingNewDateToRecoveryWorker() = runBlocking {
        val dayA = LocalDate.of(2026, 9, 19)
        val dayB = dayA.plusDays(1)
        val started = CompletableDeferred<Unit>()
        val recovered = CompletableDeferred<Unit>()
        val writes = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (command.today == dayA) {
                    started.complete(Unit)
                    awaitCancellation()
                }
                writes += command
                recovered.complete(Unit)
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(true) },
            dispatcher = Dispatchers.Unconfined
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(4).toList() }
        }
        val processing = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(SetTodayHabitCheckedUseCase.Command(1, true, dayA))
        }
        started.await()
        val pending = SetTodayHabitCheckedUseCase.Command(1, true, dayB)
        useCase(pending)
        processing.cancelAndJoin()
        withTimeout(2_000L) { recovered.await() }

        assertEquals(listOf(pending), writes)
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, dayA),
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, dayB),
                SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
                    1, true, dayA, SetTodayHabitCheckedUseCase.RollbackReason.CANCELLED
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, true, dayB)
            ),
            events.await()
        )
        // The recovered queue must release inFlight for future clicks.
        useCase(SetTodayHabitCheckedUseCase.Command(1, false, dayB))
        assertEquals(false, writes.last().targetChecked)
    }

    @Test
    fun cancelledRecoveryWriteClearsInFlightForFollowingCommand() = runBlocking {
        verifyFailedRecoveryClearsInFlight(cancelRecoveredWrite = true)
    }

    @Test
    fun failedRecoveryWriteClearsInFlightForFollowingCommand() = runBlocking {
        verifyFailedRecoveryClearsInFlight(cancelRecoveredWrite = false)
    }

    @Test
    fun laterSameHabitCommandWaitsForRecoveryWrite() = runBlocking {
        val dayA = LocalDate.of(2026, 9, 19)
        val dayB = dayA.plusDays(1)
        val firstStarted = CompletableDeferred<Unit>()
        val recoveryStarted = CompletableDeferred<Unit>()
        val releaseRecovery = CompletableDeferred<Unit>()
        val lastWriteFinished = CompletableDeferred<Unit>()
        val attempts = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        var activeWrites = 0
        var maxActiveWrites = 0
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                activeWrites++
                maxActiveWrites = maxOf(maxActiveWrites, activeWrites)
                attempts += command
                try {
                    when (attempts.size) {
                        1 -> {
                            firstStarted.complete(Unit)
                            awaitCancellation()
                        }
                        2 -> {
                            recoveryStarted.complete(Unit)
                            releaseRecovery.await()
                        }
                        3 -> lastWriteFinished.complete(Unit)
                    }
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                } finally {
                    activeWrites--
                }
            },
            observeSourceChecked = { command -> flowOf(command.targetChecked) },
            dispatcher = Dispatchers.Unconfined
        )
        val original = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(SetTodayHabitCheckedUseCase.Command(1, true, dayA))
        }
        withTimeout(2_000L) { firstStarted.await() }
        val recoveryCommand = SetTodayHabitCheckedUseCase.Command(1, false, dayB)
        val laterCommand = SetTodayHabitCheckedUseCase.Command(1, true, dayB)
        useCase(recoveryCommand)
        original.cancelAndJoin()
        try {
            withTimeout(2_000L) { recoveryStarted.await() }
            useCase(laterCommand)
            // 恢复任务尚未完成时，第三条命令只能进入同一 habitId 的等待队列。
            assertEquals(listOf(true, false), attempts.map { it.targetChecked })
        } finally {
            releaseRecovery.complete(Unit)
        }
        withTimeout(2_000L) { lastWriteFinished.await() }
        assertEquals(listOf(true, false, true), attempts.map { it.targetChecked })
        assertEquals(1, maxActiveWrites)
    }

    private suspend fun verifyFailedRecoveryClearsInFlight(cancelRecoveredWrite: Boolean) {
        val day = LocalDate.of(2026, 9, 19)
        val firstStarted = CompletableDeferred<Unit>()
        val attempts = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attempts += command.targetChecked
                when (attempts.size) {
                    1 -> {
                        firstStarted.complete(Unit)
                        awaitCancellation()
                    }
                    2 -> {
                        if (cancelRecoveredWrite) throw CancellationException("recovery cancelled")
                        error("recovery write failed")
                    }
                }
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command -> flowOf(command.targetChecked) },
            dispatcher = Dispatchers.Unconfined
        )
        val reverted = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).async(
            start = CoroutineStart.UNDISPATCHED
        ) {
            withTimeout(2_000L) {
                useCase.events.filterIsInstance<SetTodayHabitCheckedUseCase.DomainEvent.Reverted>()
                    .first { it.failedTargetChecked == false }
            }
        }
        val original = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).async(
            start = CoroutineStart.UNDISPATCHED
        ) {
            useCase(SetTodayHabitCheckedUseCase.Command(1, true, day))
        }
        withTimeout(2_000L) { firstStarted.await() }
        useCase(SetTodayHabitCheckedUseCase.Command(1, false, day))
        original.cancelAndJoin()
        val event = reverted.await()

        assertEquals(
            if (cancelRecoveredWrite) SetTodayHabitCheckedUseCase.RollbackReason.CANCELLED
            else SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED,
            event.reason
        )
        // 恢复任务失败后必须放行下一次同习惯操作，而不是永久保留 inFlight。
        withTimeout(2_000L) {
            useCase(SetTodayHabitCheckedUseCase.Command(1, true, day))
        }
        assertEquals(listOf(true, false, true), attempts)
    }
}
