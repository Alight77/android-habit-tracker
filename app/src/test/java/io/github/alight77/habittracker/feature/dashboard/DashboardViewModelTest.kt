package io.github.alight77.habittracker.feature.dashboard

import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.RecentGoalProgress
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.testutil.MainDispatcherRule
import io.github.alight77.habittracker.testutil.TestHabitDao
import io.github.alight77.habittracker.testutil.TestRecordDao
import io.github.alight77.habittracker.testutil.ThrowOnceHabitDao
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_mapsTodayCompletionAndStreakFromDaoFlows() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读")))
        val recordDao = TestRecordDao(
            listOf(
                record(habitId = 1, date = today, isDone = true),
                record(habitId = 1, date = today.minusDays(1), isDone = true)
            )
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(today)
        )

        val state = viewModel.uiState
            .filterIsInstance<DashboardUiState.Success>()
            .first { success -> success.items.singleOrNull()?.isDoneToday == true }

        assertEquals(
            HabitItemUiState(
                id = 1,
                name = "阅读",
                targetPerWeek = 7,
                isDoneToday = true,
                streak = 2,
                goalProgress = RecentGoalProgress(2, 7)
            ),
            state.items.single()
        )
    }

    @Test
    fun uiState_proratesNewHabitGoalFromCreationDay() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val createdAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读", targetPerWeek = 3, createdAt = createdAt)))
        val recordDao = TestRecordDao(listOf(record(1, today, isDone = true)))
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(today)
        )

        val state = viewModel.uiState.filterIsInstance<DashboardUiState.Success>().first()

        assertEquals(RecentGoalProgress(1, 1), state.items.single().goalProgress)
    }

    @Test
    fun onHabitChecked_updatesGoalProgressAndStreakOptimistically() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读", targetPerWeek = 3)))
        val recordDao = TestRecordDao(listOf(record(1, today.minusDays(1), isDone = true)))
        val persistGate = CompletableDeferred<Unit>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = {
                persistGate.await()
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(true) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = useCase,
            todaySource = flowOf(today),
            currentDate = { today }
        )
        viewModel.uiState.filterIsInstance<DashboardUiState.Success>().first()

        viewModel.onHabitToggle(habitId = 1)

        val state = viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
            .first { it.items.single().isDoneToday }
        assertEquals(RecentGoalProgress(2, 3), state.items.single().goalProgress)
        assertEquals(2, state.items.single().streak)
        persistGate.complete(Unit)
    }

    @Test
    fun onHabitUnchecked_updatesGoalProgressAndStreakOptimistically() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读", targetPerWeek = 3)))
        val recordDao = TestRecordDao(
            listOf(
                record(1, today, isDone = true),
                record(1, today.minusDays(1), isDone = true)
            )
        )
        val persistGate = CompletableDeferred<Unit>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = {
                persistGate.await()
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
            todaySource = flowOf(today),
            currentDate = { today }
        )
        viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
            .first { it.items.single().isDoneToday }

        viewModel.onHabitToggle(habitId = 1)

        val state = viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
            .first { !it.items.single().isDoneToday }
        assertEquals(RecentGoalProgress(1, 3), state.items.single().goalProgress)
        assertEquals(0, state.items.single().streak)
        persistGate.complete(Unit)
    }

    @Test
    fun onHabitChecked_whenUseCaseRejects_emitsFailureMessage() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读")))
        val recordDao = TestRecordDao(emptyList())
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = {
                SetTodayHabitCheckedUseCase.ApplyResult.Rejected(
                    SetTodayHabitCheckedUseCase.RejectionReason.CONFLICT
                )
            },
            observeSourceChecked = { flowOf(false) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = useCase,
            todaySource = flowOf(today),
            currentDate = { today }
        )
        viewModel.uiState.filterIsInstance<DashboardUiState.Success>().first()
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.onHabitToggle(habitId = 1)

        assertEquals(
            DashboardUiEvent.ShowMessage("打卡状态冲突，请重试"),
            event.await()
        )
    }

    @Test
    fun onHabitChecked_whenDisplayedDateIsStale_refreshesWithoutWriting() = runTest {
        val displayedDate = LocalDate.of(2026, 9, 19)
        val currentDate = displayedDate.plusDays(1)
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读")))
        val recordDao = TestRecordDao(listOf(record(1, displayedDate, isDone = true)))
        val applied = mutableListOf<SetTodayHabitCheckedUseCase.Command>()
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                applied += command
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(false) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        var refreshCount = 0
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = useCase,
            todaySource = flowOf(displayedDate),
            currentDate = { currentDate },
            requestTodayRefresh = { refreshCount++ }
        )
        viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
            .first { it.items.single().isDoneToday }
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.onHabitToggle(habitId = 1)

        assertEquals(1, refreshCount)
        assertEquals(DashboardUiEvent.ShowMessage("日期已更新，请重新操作"), event.await())
        assertEquals(emptyList<SetTodayHabitCheckedUseCase.Command>(), applied)
    }

    @Test
    fun oldDateConfirmationDoesNotClearNewDateOptimisticState() = runTest {
        val dayA = LocalDate.of(2026, 9, 19)
        val dayB = dayA.plusDays(1)
        val today = MutableStateFlow(dayA)
        val releaseA = CompletableDeferred<Unit>()
        val releaseB = CompletableDeferred<Unit>()
        val habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读")))
        val recordDao = TestRecordDao(emptyList())
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                if (command.today == dayA) releaseA.await() else releaseB.await()
                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { flowOf(true) },
            dispatcher = mainDispatcherRule.dispatcher
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = useCase,
            todaySource = today,
            currentDate = { today.value }
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        viewModel.onHabitToggle(1)
        runCurrent()
        today.value = dayB
        runCurrent()
        viewModel.onHabitToggle(1)
        runCurrent()
        assertEquals(true, (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday)

        // 旧日期先确认，新日期仍在等待写入；此时今日卡片必须保持新日期的乐观目标。
        releaseA.complete(Unit)
        runCurrent()
        assertEquals(
            "The old date's Confirmed event must leave today's pending override intact",
            true,
            (viewModel.uiState.value as DashboardUiState.Success).items.single().isDoneToday
        )
        releaseB.complete(Unit)
        runCurrent()
    }

    @Test
    fun deleteHabit_whenDaoFails_emitsFailureMessage() = runTest {
        val habitDao = ThrowingDeleteHabitDao()
        val recordDao = TestRecordDao(emptyList())
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(LocalDate.of(2026, 9, 19))
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.deleteHabit(7)

        assertEquals(
            DashboardUiEvent.ShowMessage("删除习惯失败，请稍后重试"),
            event.await()
        )
    }

    @Test
    fun retry_afterHabitFlowFails_resubscribesAndEmitsSuccess() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = ThrowOnceHabitDao(listOf(habit(id = 1, name = "阅读")))
        val recordDao = TestRecordDao(emptyList())
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(today)
        )

        val error = viewModel.uiState
            .filterIsInstance<DashboardUiState.Error>()
            .first()

        assertEquals("加载习惯数据失败，请重试", error.message)

        viewModel.retry()

        val success = viewModel.uiState
            .filterIsInstance<DashboardUiState.Success>()
            .first { state -> state.items.singleOrNull()?.name == "阅读" }

        assertEquals("阅读", success.items.single().name)
        assertEquals(2, habitDao.subscriptionCount)
    }

    private fun acceptedUseCase(): SetTodayHabitCheckedUseCase {
        return SetTodayHabitCheckedUseCase(
            applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
            observeSourceChecked = { flowOf(false) },
            dispatcher = mainDispatcherRule.dispatcher
        )
    }

    private fun habit(
        id: Int,
        name: String,
        targetPerWeek: Int = 7,
        createdAt: Long = 0
    ): HabitEntity {
        return HabitEntity(
            id = id,
            name = name,
            description = "",
            targetPerWeek = targetPerWeek,
            createdAt = createdAt
        )
    }

    private fun record(habitId: Int, date: LocalDate, isDone: Boolean): RecordEntity {
        return RecordEntity(
            habitId = habitId,
            date = date.toEpochDay(),
            isDone = isDone
        )
    }

    private class ThrowingDeleteHabitDao : HabitDao {
        override suspend fun insertHabit(habit: HabitEntity) = Unit
        override suspend fun deleteHabit(habit: HabitEntity) = Unit
        override suspend fun getHabitById(habitId: Int): HabitEntity? = null
        override suspend fun updateHabit(habit: HabitEntity) = Unit

        override suspend fun deleteHabitById(habitId: Int) {
            error("database unavailable")
        }

        override fun getAllHabits() = flowOf(emptyList<HabitEntity>())
    }
}
