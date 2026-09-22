package io.github.alight77.habittracker.domain.usecase.dashboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

class SetTodayHabitCheckedUseCase(
    private val applyCommand: suspend (Command) -> ApplyResult,
    private val observeSourceChecked: (Command) -> Flow<Boolean>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val confirmationTimeoutMillis: Long = 1_500L
) {

    private val stateMutex = Mutex()
    private val _events = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    private val optimisticChecked = mutableMapOf<Int, Boolean>()
    private val inFlight = mutableSetOf<Int>()
    private val pendingCommand = mutableMapOf<Int, Command>()

    val events: SharedFlow<DomainEvent> = _events.asSharedFlow()

    suspend operator fun invoke(command: Command) {
        withContext(dispatcher) {
            val event = stateMutex.withLock {
                if (command.habitId in inFlight) {
                    val oldTargetChecked = optimisticChecked[command.habitId] ?: command.targetChecked
                    pendingCommand[command.habitId] = command
                    optimisticChecked[command.habitId] = command.targetChecked

                    DomainEvent.Superseded(
                        habitId = command.habitId,
                        oldTargetChecked = oldTargetChecked,
                        newTargetChecked = command.targetChecked,
                        today = command.today
                    )
                } else {
                    inFlight += command.habitId
                    optimisticChecked[command.habitId] = command.targetChecked

                    DomainEvent.OptimisticApplied(
                        habitId = command.habitId,
                        targetChecked = command.targetChecked,
                        today = command.today
                    )
                }
            }

            _events.emit(event)

            if (event is DomainEvent.OptimisticApplied) {
                processQueue(command)
            }
        }
    }

    private suspend fun processQueue(initialCommand: Command) {
        var currentCommand = initialCommand

        while (true) {
            val result = persistAndConfirm(currentCommand)
            val decision = stateMutex.withLock {
                val pending = pendingCommand.remove(currentCommand.habitId)

                if (pending != null) {
                    optimisticChecked[currentCommand.habitId] = pending.targetChecked
                    QueueDecision.Continue(pending)
                } else {
                    inFlight -= currentCommand.habitId
                    optimisticChecked -= currentCommand.habitId
                    QueueDecision.Finish(result.toDomainEvent(currentCommand))
                }
            }

            when (decision) {
                is QueueDecision.Continue -> currentCommand = decision.command
                is QueueDecision.Finish -> {
                    _events.emit(decision.event)
                    return
                }
            }
        }
    }

    private suspend fun persistAndConfirm(command: Command): ProcessResult {
        return try {
            when (val result = applyCommand(command)) {
                ApplyResult.Accepted -> {
                    if (awaitSourceConfirmed(command)) {
                        ProcessResult.Confirmed
                    } else {
                        ProcessResult.Reverted(RollbackReason.CONVERGENCE_TIMEOUT)
                    }
                }

                is ApplyResult.Rejected -> ProcessResult.Rejected(result.reason)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            ProcessResult.Reverted(RollbackReason.PERSIST_FAILED)
        }
    }

    private suspend fun awaitSourceConfirmed(command: Command): Boolean {
        return try {
            withTimeoutOrNull(confirmationTimeoutMillis) {
                observeSourceChecked(command)
                    .distinctUntilChanged()
                    .first { checked -> checked == command.targetChecked }
                true
            } ?: false
        } catch (_: NoSuchElementException) {
            false
        }
    }

    private fun ProcessResult.toDomainEvent(command: Command): DomainEvent {
        return when (this) {
            ProcessResult.Confirmed -> DomainEvent.Confirmed(
                habitId = command.habitId,
                targetChecked = command.targetChecked,
                today = command.today
            )

            is ProcessResult.Reverted -> DomainEvent.Reverted(
                habitId = command.habitId,
                failedTargetChecked = command.targetChecked,
                today = command.today,
                reason = reason
            )

            is ProcessResult.Rejected -> DomainEvent.Rejected(
                habitId = command.habitId,
                targetChecked = command.targetChecked,
                today = command.today,
                reason = reason
            )
        }
    }

    data class Command(
        val habitId: Int,
        val targetChecked: Boolean,
        val today: LocalDate
    )

    sealed interface DomainEvent {
        data class OptimisticApplied(
            val habitId: Int,
            val targetChecked: Boolean,
            val today: LocalDate
        ) : DomainEvent

        data class Confirmed(
            val habitId: Int,
            val targetChecked: Boolean,
            val today: LocalDate
        ) : DomainEvent

        data class Superseded(
            val habitId: Int,
            val oldTargetChecked: Boolean,
            val newTargetChecked: Boolean,
            val today: LocalDate
        ) : DomainEvent

        data class Reverted(
            val habitId: Int,
            val failedTargetChecked: Boolean,
            val today: LocalDate,
            val reason: RollbackReason
        ) : DomainEvent

        data class Rejected(
            val habitId: Int,
            val targetChecked: Boolean,
            val today: LocalDate,
            val reason: RejectionReason
        ) : DomainEvent
    }

    enum class RejectionReason {
        HABIT_NOT_FOUND,
        CONFLICT
    }

    enum class RollbackReason {
        PERSIST_FAILED,
        CONVERGENCE_TIMEOUT
    }

    sealed interface ApplyResult {
        data object Accepted : ApplyResult
        data class Rejected(val reason: RejectionReason) : ApplyResult
    }

    private sealed interface ProcessResult {
        data object Confirmed : ProcessResult
        data class Reverted(val reason: RollbackReason) : ProcessResult
        data class Rejected(val reason: RejectionReason) : ProcessResult
    }

    private sealed interface QueueDecision {
        data class Continue(val command: Command) : QueueDecision
        data class Finish(val event: DomainEvent) : QueueDecision
    }
}
