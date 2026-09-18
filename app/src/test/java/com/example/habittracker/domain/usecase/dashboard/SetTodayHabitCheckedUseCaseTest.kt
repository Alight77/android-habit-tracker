package com.example.habittracker.domain.usecase.dashboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    private fun command(targetChecked: Boolean): SetTodayHabitCheckedUseCase.Command {
        return SetTodayHabitCheckedUseCase.Command(
            habitId = 1,
            targetChecked = targetChecked,
            today = today
        )
    }
}