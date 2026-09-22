package io.github.alight77.habittracker.domain.usecase.dashboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SetTodayHabitCheckedUseCaseTest {

    private val today: LocalDate = LocalDate.of(2026, 4, 26)

    @Test
    fun invoke_emitsConfirmedWhenSourceMatchesAfterPersist() = runBlocking {
        val sourceChecked = MutableStateFlow(false)
        var applyCount = 0
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                applyCount += 1
                sourceChecked.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { sourceChecked },
            dispatcher = Dispatchers.Unconfined
        )
        val command = command(targetChecked = true)
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(2).toList() }
        }

        useCase(command)

        assertEquals(1, applyCount)
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(
                    habitId = 1,
                    targetChecked = true,
                    today = today
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(
                    habitId = 1,
                    targetChecked = true,
                    today = today
                )
            ),
            events.await()
        )
    }

    @Test
    fun invoke_cleansUpInFlightAfterConfirmedSoLaterCommandPersists() = runBlocking {
        val sourceChecked = MutableStateFlow(false)
        var applyCount = 0
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                applyCount += 1
                sourceChecked.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { sourceChecked },
            dispatcher = Dispatchers.Unconfined
        )

        val firstEvents = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(2).toList() }
        }
        useCase(command(targetChecked = true))
        firstEvents.await()

        val secondEvents = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(2).toList() }
        }
        useCase(command(targetChecked = false))

        assertEquals(2, applyCount)
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(
                    habitId = 1,
                    targetChecked = false,
                    today = today
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(
                    habitId = 1,
                    targetChecked = false,
                    today = today
                )
            ),
            secondEvents.await()
        )
    }

    @Test
    fun invoke_whenInFlightCommandIsCancelled_cleansUpSoLaterCommandPersists() = runBlocking {
        val sourceChecked = MutableStateFlow(false)
        val firstApplyStarted = CompletableDeferred<Unit>()
        var applyCount = 0
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                applyCount += 1
                if (applyCount == 1) {
                    firstApplyStarted.complete(Unit)
                    awaitCancellation()
                }
                sourceChecked.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { sourceChecked },
            dispatcher = Dispatchers.Unconfined
        )

        val cancelledCommand = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(command(targetChecked = true))
        }
        firstApplyStarted.await()
        cancelledCommand.cancelAndJoin()

        useCase(command(targetChecked = false))

        assertEquals(2, applyCount)
    }

    @Test
    fun invoke_drainsLatestPendingTargetAfterInFlightCommandConfirms() = runBlocking {
        val sourceChecked = MutableStateFlow(false)
        val firstApplyCanFinish = CompletableDeferred<Unit>()
        val firstApplyStarted = CompletableDeferred<Unit>()
        var applyCount = 0
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                applyCount += 1
                if (applyCount == 1) {
                    firstApplyStarted.complete(Unit)
                    firstApplyCanFinish.await()
                }
                sourceChecked.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { sourceChecked },
            dispatcher = Dispatchers.Unconfined
        )

        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(3).toList() }
        }
        val firstCommand = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(command(targetChecked = true))
        }
        firstApplyStarted.await()

        useCase(command(targetChecked = false))
        firstApplyCanFinish.complete(Unit)
        firstCommand.await()

        assertEquals(2, applyCount)
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(
                    habitId = 1,
                    targetChecked = true,
                    today = today
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Superseded(
                    habitId = 1,
                    oldTargetChecked = true,
                    newTargetChecked = false,
                    today = today
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(
                    habitId = 1,
                    targetChecked = false,
                    today = today
                )
            ),
            events.await()
        )
    }

    @Test
    fun invoke_whenPendingCommandCrossesMidnight_persistsLatestCommandDate() = runBlocking {
        val sourceChecked = MutableStateFlow(false)
        val firstApplyCanFinish = CompletableDeferred<Unit>()
        val firstApplyStarted = CompletableDeferred<Unit>()
        val appliedCommands = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val nextDay = today.plusDays(1)
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                appliedCommands += command
                if (appliedCommands.size == 1) {
                    firstApplyStarted.complete(Unit)
                    firstApplyCanFinish.await()
                }
                sourceChecked.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { sourceChecked },
            dispatcher = Dispatchers.Unconfined
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(3).toList() }
        }
        val firstCommand = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(command(targetChecked = true))
        }
        firstApplyStarted.await()

        val latestCommand = command(targetChecked = false, date = nextDay)
        useCase(latestCommand)
        firstApplyCanFinish.complete(Unit)
        firstCommand.await()

        assertEquals(listOf(command(targetChecked = true), latestCommand), appliedCommands)
        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(1, true, today),
                SetTodayHabitCheckedUseCase.DomainEvent.Superseded(1, true, false, nextDay),
                SetTodayHabitCheckedUseCase.DomainEvent.Confirmed(1, false, nextDay)
            ),
            events.await()
        )
    }

    @Test
    fun invoke_whenLatestPendingCommandTimesOut_emitsRevertedWithLatestDate() = runBlocking {
        val firstApplyCanFinish = CompletableDeferred<Unit>()
        val firstApplyStarted = CompletableDeferred<Unit>()
        val appliedCommands = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val nextDay = today.plusDays(1)
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                appliedCommands += command
                if (appliedCommands.size == 1) {
                    firstApplyStarted.complete(Unit)
                    firstApplyCanFinish.await()
                }
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(true) },
            dispatcher = Dispatchers.Unconfined,
            confirmationTimeoutMillis = 10L
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(3).toList() }
        }
        val firstCommand = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(command(targetChecked = true))
        }
        firstApplyStarted.await()

        val latestCommand = command(targetChecked = false, date = nextDay)
        useCase(latestCommand)
        firstApplyCanFinish.complete(Unit)
        firstCommand.await()

        assertEquals(listOf(command(targetChecked = true), latestCommand), appliedCommands)
        assertEquals(
            SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
                habitId = 1,
                failedTargetChecked = false,
                today = nextDay,
                reason = SetTodayHabitCheckedUseCase.RollbackReason.CONVERGENCE_TIMEOUT
            ),
            events.await().last()
        )
    }

    @Test
    fun invoke_whenLatestPendingCommandPersistenceFails_emitsRevertedWithLatestDate() = runBlocking {
        val sourceChecked = MutableStateFlow(false)
        val firstApplyCanFinish = CompletableDeferred<Unit>()
        val firstApplyStarted = CompletableDeferred<Unit>()
        val appliedCommands = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val nextDay = today.plusDays(1)
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                appliedCommands += command
                if (appliedCommands.size == 1) {
                    firstApplyStarted.complete(Unit)
                    firstApplyCanFinish.await()
                    sourceChecked.value = command.targetChecked
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                } else {
                    error("database unavailable")
                }
            },
            observeSourceChecked = { sourceChecked },
            dispatcher = Dispatchers.Unconfined
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(3).toList() }
        }
        val firstCommand = async(start = CoroutineStart.UNDISPATCHED) {
            useCase(command(targetChecked = true))
        }
        firstApplyStarted.await()

        val latestCommand = command(targetChecked = false, date = nextDay)
        useCase(latestCommand)
        firstApplyCanFinish.complete(Unit)
        firstCommand.await()

        assertEquals(listOf(command(targetChecked = true), latestCommand), appliedCommands)
        assertEquals(
            SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
                habitId = 1,
                failedTargetChecked = false,
                today = nextDay,
                reason = SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED
            ),
            events.await().last()
        )
    }

    @Test
    fun invoke_emitsRevertedWhenSourceDoesNotConverge() = runBlocking {
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
            observeSourceChecked = { flowOf(false) },
            dispatcher = Dispatchers.Unconfined,
            confirmationTimeoutMillis = 10L
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(2).toList() }
        }

        useCase(command(targetChecked = true))

        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(
                    habitId = 1,
                    targetChecked = true,
                    today = today
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
                    habitId = 1,
                    failedTargetChecked = true,
                    today = today,
                    reason = SetTodayHabitCheckedUseCase.RollbackReason.CONVERGENCE_TIMEOUT
                )
            ),
            events.await()
        )
    }

    @Test
    fun invoke_emitsRevertedWhenPersistThrows() = runBlocking {
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { error("database unavailable") },
            observeSourceChecked = { flowOf(false) },
            dispatcher = Dispatchers.Unconfined,
            confirmationTimeoutMillis = 10L
        )
        val events = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { useCase.events.take(2).toList() }
        }

        useCase(command(targetChecked = true))

        assertEquals(
            listOf(
                SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied(
                    habitId = 1,
                    targetChecked = true,
                    today = today
                ),
                SetTodayHabitCheckedUseCase.DomainEvent.Reverted(
                    habitId = 1,
                    failedTargetChecked = true,
                    today = today,
                    reason = SetTodayHabitCheckedUseCase.RollbackReason.PERSIST_FAILED
                )
            ),
            events.await()
        )
    }

    private fun command(
        targetChecked: Boolean,
        date: LocalDate = today
    ): SetTodayHabitCheckedUseCase.Command {
        return SetTodayHabitCheckedUseCase.Command(
            habitId = 1,
            targetChecked = targetChecked,
            today = date
        )
    }
}
