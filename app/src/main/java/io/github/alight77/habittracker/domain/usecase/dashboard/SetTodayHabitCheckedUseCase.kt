package io.github.alight77.habittracker.domain.usecase.dashboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    // 调用方协程取消后，已入队命令和事件投递由应用级 UseCase 接管；进程死亡不在此保证范围内。
    private val recoveryScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _events = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    // 慢订阅者不能占住状态锁；无限队列保留终态事件并按序投递，持续阻塞时会累积内存。
    private val eventQueue = Channel<DomainEvent>(Channel.UNLIMITED)

    init {
        recoveryScope.launch {
            for (event in eventQueue) _events.emit(event)
        }
    }

    // 乐观目标与待处理命令都按日期区分；同日取最后目标，不同日期分别保留。
    private val optimisticChecked = mutableMapOf<Pair<Int, LocalDate>, Boolean>()
    private val inFlight = mutableSetOf<Int>()
    private val pendingCommand = mutableMapOf<Int, LinkedHashMap<LocalDate, Command>>()

    val events: SharedFlow<DomainEvent> = _events.asSharedFlow()

    suspend operator fun invoke(command: Command) {
        withContext(dispatcher) {
            val admission = updateStateAndEmit {
                val key = command.habitId to command.today
                val oldTargetChecked = optimisticChecked.put(key, command.targetChecked)
                val result = if (command.habitId in inFlight) {
                    pendingCommand.getOrPut(command.habitId) { linkedMapOf() }[command.today] = command
                    QueueAdmission(
                        // 新日期是独立操作，只有同日期已有目标时才算 Superseded。
                        event = if (oldTargetChecked == null) {
                            DomainEvent.OptimisticApplied(command.habitId, command.targetChecked, command.today)
                        } else {
                            DomainEvent.Superseded(
                                command.habitId, oldTargetChecked, command.targetChecked, command.today
                            )
                        },
                        startsQueue = false
                    )
                } else {
                    inFlight += command.habitId
                    QueueAdmission(
                        event = DomainEvent.OptimisticApplied(
                            command.habitId, command.targetChecked, command.today
                        ),
                        startsQueue = true
                    )
                }
                result to result.event
            }

            if (admission.startsQueue) {
                processQueue(command)
            }
        }
    }

    private suspend fun processQueue(initialCommand: Command) {
        var currentCommand = initialCommand
        var queueFinished = false

        try {
            while (true) {
                val result = persistAndConfirm(currentCommand)
                val decision = updateStateAndEmit {
                    val habitId = currentCommand.habitId
                    val pendingByDate = pendingCommand[habitId]
                    val hasReplacement = pendingByDate?.containsKey(currentCommand.today) == true
                    // 同日还有更新意图时，此次写入不是该日期的终态，不能清除乐观目标。
                    val completedEvent = if (hasReplacement) null else {
                        optimisticChecked.remove(habitId to currentCommand.today)
                        result.toDomainEvent(currentCommand)
                    }
                    val next = takeNextPending(habitId)

                    if (next == null) {
                        inFlight -= currentCommand.habitId
                        queueFinished = true
                    } else {
                        // 先记录已出队命令；若此处发生取消，finally 才能接管正确的命令。
                        currentCommand = next
                    }
                    val resultDecision = QueueDecision(next, completedEvent)
                    resultDecision to completedEvent
                }

                val next = decision.nextCommand
                if (next == null) return
            }
        } finally {
            if (!queueFinished) {
                // 只中断当前命令，剩余日期交给恢复协程继续串行写入，而非清空队列。
                withContext(NonCancellable) {
                    val recovery = updateStateAndEmit {
                        val habitId = currentCommand.habitId
                        val hasReplacement = pendingCommand[habitId]
                            ?.containsKey(currentCommand.today) == true
                        val cancelledEvent = if (hasReplacement) null else {
                            optimisticChecked.remove(habitId to currentCommand.today)
                            DomainEvent.Reverted(
                                habitId, currentCommand.targetChecked, currentCommand.today,
                                RollbackReason.CANCELLED
                            )
                        }
                        val next = takeNextPending(habitId)
                        if (next == null) {
                            inFlight -= habitId
                            optimisticChecked.keys.removeAll { it.first == habitId }
                        }
                        val resultDecision = QueueDecision(next, cancelledEvent)
                        resultDecision to cancelledEvent
                    }
                    recovery.nextCommand?.let { next ->
                        recoveryScope.launch { processQueue(next) }
                    }
                }
            }
        }
    }

    /** 调用方须持有 stateMutex；LinkedHashMap 保留不同日期首次入队的顺序。 */
    private fun takeNextPending(habitId: Int): Command? {
        val pendingByDate = pendingCommand[habitId] ?: return null
        val next = pendingByDate.entries.firstOrNull()?.let { pendingByDate.remove(it.key) }
        if (pendingByDate.isEmpty()) pendingCommand.remove(habitId)
        return next
    }

    /** 状态变化与事件入队共用一把短锁；只有取消后的清理路径需要 NonCancellable。 */
    private suspend fun <T> updateStateAndEmit(transition: () -> Pair<T, DomainEvent?>): T =
        stateMutex.withLock {
            val (result, event) = transition()
            event?.let { eventQueue.trySend(it).getOrThrow() }
            result
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
        CONVERGENCE_TIMEOUT,
        CANCELLED
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

    private data class QueueAdmission(val event: DomainEvent, val startsQueue: Boolean)

    private data class QueueDecision(
        val nextCommand: Command?,
        val completedEvent: DomainEvent?
    )
}
