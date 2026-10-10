package io.github.alight77.habittracker.feature.dashboard

import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.testutil.MainDispatcherRule
import io.github.alight77.habittracker.testutil.TestHabitDao
import io.github.alight77.habittracker.testutil.TestRecordDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRapidToggleTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    private val today = LocalDate.of(2026, 9, 19)

    @Test
    fun twoClicksBeforeRecompositionSendTrueThenFalse() = runTest {
        verifyRapidClicks(initialChecked = false, expectedTargets = listOf(true, false))
    }

    @Test
    fun threeClicksBeforeRecompositionSendTrueFalseTrue() = runTest {
        verifyRapidClicks(initialChecked = false, expectedTargets = listOf(true, false, true))
    }

    @Test
    fun twoClicksFromCompletedStateSendFalseThenTrue() = runTest {
        verifyRapidClicks(initialChecked = true, expectedTargets = listOf(false, true))
    }

    @Test
    fun newDateIsRejectedWhileDashboardStillShowsPreviousDate() = runTest {
        val dayA = today
        val dayB = dayA.plusDays(1)
        val dateSource = MutableStateFlow(dayA)
        var systemDate = dayA
        var refreshCount = 0
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(
            listOf(RecordEntity(habitId = 1, date = dayA.toEpochDay(), isDone = true))
        )
        val submitted = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                submitted += command
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(false) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = useCase,
            todaySource = dateSource,
            currentDate = { systemDate },
            requestTodayRefresh = { refreshCount++ }
        )
        val dashboardCollector = backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(true, (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday)

        dashboardCollector.cancel()
        advanceTimeBy(5_001L)
        runCurrent()
        // 单独保持日期流订阅，使其先到 Day B，而已停订阅的 UI StateFlow 仍保留 Day A 快照。
        val todayField = DashboardViewModel::class.java.getDeclaredField("todayFlow")
        todayField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val observedToday = todayField.get(viewModel) as StateFlow<LocalDate>
        backgroundScope.launch { observedToday.collect() }
        runCurrent()
        systemDate = dayB
        dateSource.value = dayB
        runCurrent()
        assertEquals(dayB, observedToday.value)
        assertEquals(true, (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday)
        val warning = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.first() }
        }

        viewModel.onHabitToggle(1)
        runCurrent()

        assertEquals(emptyList<SetTodayHabitCheckedUseCase.Command>(), submitted)
        assertEquals(1, refreshCount)
        assertEquals(DashboardUiEvent.ShowMessage("日期已更新，请重新操作"), warning.await())
    }

    @Test
    fun supersededRevertedDoesNotShowStaleFailureMessage() = runTest {
        verifySupersededFailureDoesNotShowMessage(rejected = false)
    }

    @Test
    fun supersededRejectedDoesNotShowStaleFailureMessage() = runTest {
        verifySupersededFailureDoesNotShowMessage(rejected = true)
    }

    @Test
    fun pendingCommandFailureAfterDashboardRecreationStillShowsMessage() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val releaseWrite = CompletableDeferred<Unit>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = {
                releaseWrite.await()
                error("database unavailable")
            },
            observeSourceChecked = { flowOf(false) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val command = SetTodayHabitCheckedUseCase.Command(
            habitId = 1,
            targetChecked = true,
            today = today,
            intentId = useCase.nextIntentId()
        )
        backgroundScope.launch { useCase(command) }
        runCurrent()

        // 原页面已消失，新 ViewModel 没收到 OptimisticApplied，但仍应提示这笔有效命令的失败。
        val viewModel = viewModel(habitDao, recordDao, useCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        val warning = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.first() }
        }
        runCurrent()
        releaseWrite.complete(Unit)
        runCurrent()

        assertEquals(DashboardUiEvent.ShowMessage("打卡保存失败，请稍后重试"), warning.await())
    }

    @Test
    fun failedClickDoesNotPoisonNextToggleTarget() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val attemptedTargets = mutableListOf<Boolean>()
        val source = MutableStateFlow(false)
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                if (attemptedTargets.size == 1) error("database unavailable")
                source.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { source },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        val failure = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.first() }
        }

        viewModel.onHabitToggle(1)
        runCurrent()
        assertEquals(DashboardUiEvent.ShowMessage("打卡保存失败，请稍后重试"), failure.await())
        viewModel.onHabitToggle(1)
        runCurrent()

        // 失败事件清除本地目标后，再点击应基于未打卡的实际状态重新切换到 true。
        assertEquals(listOf(true, true), attemptedTargets)
        assertEquals(true, source.value)
    }

    @Test
    fun delayedOldConfirmationCannotClearNewerSameTarget() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val source = MutableStateFlow(false)
        val attemptedTargets = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                source.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { source },
            dispatcher = Dispatchers.Unconfined
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        // UseCase 即刻写入，但主线程事件收集暂不运行，制造旧 Confirmed 晚于新点击的顺序。
        repeat(3) { viewModel.onHabitToggle(1) }
        assertEquals(listOf(true, false, true), attemptedTargets)
        runCurrent()
        assertEquals(
            true,
            (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday
        )

        viewModel.onHabitToggle(1)
        assertEquals(false, attemptedTargets.last())
    }

    @Test
    fun failedSecondClickRestoresRoomStateAndNextToggleUsesIt() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val repository = HabitRepository(habitDao, recordDao)
        val releaseFirst = CompletableDeferred<Unit>()
        val attemptedTargets = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                when (attemptedTargets.size) {
                    1 -> releaseFirst.await()
                    2 -> error("database unavailable")
                }
                repository.setTodayRecordChecked(
                    command.habitId, command.today.toEpochDay(), command.targetChecked
                )
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
            },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        viewModel.onHabitToggle(1)
        viewModel.onHabitToggle(1)
        runCurrent()
        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(listOf(true, false), attemptedTargets)
        assertEquals(true, recordDao.getRecordByDate(1, today.toEpochDay())?.isDone)
        assertEquals(
            true,
            (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday
        )
        // 第二笔失败时只回滚第二笔意图；下一次点击应将已提交的 true 切换为 false。
        viewModel.onHabitToggle(1)
        runCurrent()
        assertEquals(listOf(true, false, false), attemptedTargets)
        assertEquals(false, recordDao.getRecordByDate(1, today.toEpochDay())?.isDone)
    }

    @Test
    fun failedSecondClickWithDelayedDashboardFlowUsesCommittedRoomState() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val releaseDashboardFlow = CompletableDeferred<Unit>()
        val delayedDashboardDao = object : RecordDao by recordDao {
            override fun getAllRecords() = flow {
                recordDao.getAllRecords().collect { records ->
                    // 单条记录的确认流先到达；仪表盘全量列表刻意晚于失败事件。
                    if (records.isNotEmpty()) releaseDashboardFlow.await()
                    emit(records)
                }
            }
        }
        val repository = HabitRepository(habitDao, recordDao)
        val attemptedTargets = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                if (attemptedTargets.size == 2) error("database unavailable")
                repository.setTodayRecordChecked(
                    command.habitId, command.today.toEpochDay(), command.targetChecked
                )
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
            },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = delayedDashboardDao,
            repository = repository,
            setTodayHabitChecked = useCase,
            todaySource = flowOf(today),
            currentDate = { today }
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        viewModel.onHabitToggle(1)
        runCurrent()
        assertEquals(true, recordDao.getRecordByDate(1, today.toEpochDay())?.isDone)
        viewModel.onHabitToggle(1)
        runCurrent()
        viewModel.onHabitToggle(1)
        runCurrent()

        assertEquals(listOf(true, false, false), attemptedTargets)
        releaseDashboardFlow.complete(Unit)
        runCurrent()
        assertEquals(false, recordDao.getRecordByDate(1, today.toEpochDay())?.isDone)
    }

    @Test
    fun delayedFailureReadCannotReplaceNewerClick() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val dashboardDao = object : RecordDao by recordDao {
            override suspend fun getRecordByDate(habitId: Int, epochDay: Long): RecordEntity? {
                readStarted.complete(Unit)
                releaseRead.await()
                return recordDao.getRecordByDate(habitId, epochDay)
            }
        }
        val repository = HabitRepository(habitDao, recordDao)
        val attemptedTargets = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                if (attemptedTargets.size == 2) error("database unavailable")
                repository.setTodayRecordChecked(
                    command.habitId, command.today.toEpochDay(), command.targetChecked
                )
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                repository.observeTodayRecord(command.habitId, command.today.toEpochDay())
            },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = dashboardDao,
            repository = repository,
            setTodayHabitChecked = useCase,
            todaySource = flowOf(today),
            currentDate = { today }
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        viewModel.onHabitToggle(1)
        runCurrent()
        viewModel.onHabitToggle(1)
        runCurrent()
        assertEquals(true, readStarted.isCompleted)

        // 旧失败事件等待读库时，新点击已经产生更大的 intentId。
        viewModel.onHabitToggle(1)
        runCurrent()
        releaseRead.complete(Unit)
        runCurrent()

        assertEquals(listOf(true, false, true), attemptedTargets)
        assertEquals(true, recordDao.getRecordByDate(1, today.toEpochDay())?.isDone)
        assertEquals(
            true,
            (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday
        )
    }

    @Test
    fun confirmationTimeoutUsesPersistedStateForFollowingToggle() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val repository = HabitRepository(habitDao, recordDao)
        val attemptedTargets = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                repository.setTodayRecordChecked(
                    command.habitId, command.today.toEpochDay(), command.targetChecked
                )
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = {
                // Room 已写入，但此处故意延迟确认信号，固定 CONVERGENCE_TIMEOUT 时序。
                flow { emit(false); awaitCancellation() }
            },
            dispatcher = mainDispatcherRule.dispatcher,
            confirmationTimeoutMillis = 10L
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        val timeoutMessage = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.first() }
        }

        viewModel.onHabitToggle(1)
        runCurrent()
        advanceTimeBy(10L)
        runCurrent()
        assertEquals(
            DashboardUiEvent.ShowMessage("打卡状态同步超时，请重试"),
            timeoutMessage.await()
        )
        assertEquals(true, recordDao.getRecordByDate(1, today.toEpochDay())?.isDone)

        viewModel.onHabitToggle(1)
        runCurrent()
        assertEquals(listOf(true, false), attemptedTargets)
    }

    @Test
    fun blockedHabitDoesNotChangeAnotherHabitsToggleBaseline() = runTest {
        val habitDao = TestHabitDao(listOf(habit(1), habit(2)))
        val recordDao = TestRecordDao(emptyList())
        val firstGate = CompletableDeferred<Unit>()
        val attempted = mutableListOf<Pair<Int, Boolean>>()
        val checked = mutableMapOf(1 to false, 2 to false)
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (command.habitId == 1) firstGate.await()
                attempted += command.habitId to command.targetChecked
                checked[command.habitId] = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command -> flowOf(checked[command.habitId] == true) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        viewModel.onHabitToggle(1)
        viewModel.onHabitToggle(1)
        viewModel.onHabitToggle(2)
        runCurrent()
        assertEquals(listOf(2 to true), attempted)
        firstGate.complete(Unit)
        runCurrent()

        assertEquals(false, checked[1])
        assertEquals(true, checked[2])
    }

    private suspend fun TestScope.verifyRapidClicks(
        initialChecked: Boolean,
        expectedTargets: List<Boolean>
    ) {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(
            if (initialChecked) {
                listOf(RecordEntity(habitId = 1, date = today.toEpochDay(), isDone = true))
            } else emptyList()
        )
        val source = MutableStateFlow(initialChecked)
        val firstGate = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (writes.isEmpty()) firstGate.await()
                writes += command.targetChecked
                source.value = command.targetChecked
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { source },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        val acceptedTargets = mutableListOf<Boolean>()
        backgroundScope.launch { viewModel.uiState.collect() }
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCase.events.collect { event ->
                when (event) {
                    is SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied ->
                        acceptedTargets += event.targetChecked
                    is SetTodayHabitCheckedUseCase.DomainEvent.Superseded ->
                        acceptedTargets += event.newTargetChecked
                    else -> Unit
                }
            }
        }
        runCurrent()

        // 同一 UI 快照下连续调用，不等待 DomainEvent、Room 或 Compose 重组。
        repeat(expectedTargets.size) { viewModel.onHabitToggle(1) }
        runCurrent()
        assertEquals(expectedTargets, acceptedTargets)
        firstGate.complete(Unit)
        runCurrent()

        assertEquals(expectedTargets.last(), source.value)
        assertEquals(expectedTargets.last(), writes.last())
    }

    private suspend fun TestScope.verifySupersededFailureDoesNotShowMessage(rejected: Boolean) {
        val habitDao = TestHabitDao(listOf(habit(1)))
        val recordDao = TestRecordDao(emptyList())
        val source = MutableStateFlow(false)
        val attemptedTargets = mutableListOf<Boolean>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                attemptedTargets += command.targetChecked
                if (attemptedTargets.size == 1) {
                    if (rejected) {
                        SetTodayHabitCheckedUseCase.ApplyResult.Rejected(
                            SetTodayHabitCheckedUseCase.RejectionReason.CONFLICT
                        )
                    } else {
                        error("database unavailable")
                    }
                } else {
                    source.value = command.targetChecked
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                }
            },
            observeSourceChecked = { source },
            dispatcher = Dispatchers.Unconfined
        )
        val viewModel = viewModel(habitDao, recordDao, useCase)
        val messages = mutableListOf<DashboardUiEvent>()
        backgroundScope.launch { viewModel.uiState.collect() }
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { messages += it }
        }
        runCurrent()

        // UseCase 先完成旧失败，主线程暂不收集事件；新点击在旧失败到达 ViewModel 前已接受。
        viewModel.onHabitToggle(1)
        viewModel.onHabitToggle(1)
        runCurrent()

        assertEquals(listOf(true, false), attemptedTargets)
        assertEquals(emptyList<DashboardUiEvent>(), messages)
        assertEquals(false, (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday)
    }

    private fun viewModel(
        habitDao: TestHabitDao,
        recordDao: TestRecordDao,
        useCase: SetTodayHabitCheckedUseCase
    ) = DashboardViewModel(
        habitDao = habitDao,
        recordDao = recordDao,
        repository = HabitRepository(habitDao, recordDao),
        setTodayHabitChecked = useCase,
        todaySource = flowOf(today),
        currentDate = { today }
    )

    private fun habit(id: Int) = HabitEntity(
        id = id,
        name = "阅读$id",
        description = "",
        targetPerWeek = 7,
        createdAt = 0L
    )
}
