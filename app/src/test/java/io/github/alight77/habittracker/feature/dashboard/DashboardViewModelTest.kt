package io.github.alight77.habittracker.feature.dashboard

import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.testutil.MainDispatcherRule
import io.github.alight77.habittracker.testutil.TestHabitDao
import io.github.alight77.habittracker.testutil.TestRecordDao
import io.github.alight77.habittracker.testutil.ThrowOnceHabitDao
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
                recentDays = recentCheckInDays(
                    today.minusDays(6),
                    listOf(today, today.minusDays(1)),
                    today
                )
            ),
            state.items.single()
        )
    }

    @Test
    fun uiState_keepsKnownAndUnknownIconKeysWithoutChangingStoredValues() = runTest {
        val habitDao = TestHabitDao(
            listOf(
                habit(id = 1, name = "阅读").copy(iconKey = "reading"),
                habit(id = 2, name = "骑行").copy(iconKey = "cycling")
            )
        )
        val recordDao = TestRecordDao(emptyList())
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(LocalDate.of(2026, 9, 19))
        )

        val items = viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
            .first { it.items.size == 2 }.items
        assertEquals(IconKey.READING, items[0].iconKey)
        assertEquals(IconKey("cycling"), items[1].iconKey)
        assertEquals("cycling", habitDao.getHabitById(2)?.iconKey)
    }

    @Test
    fun uiState_keepsFullTargetAndMarksDaysBeforeCreation() = runTest {
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

        val item = state.items.single()
        assertEquals(3, item.targetPerWeek)
        assertEquals(1, item.streak)
        assertEquals(List(6) { RecentDayStatus.BEFORE_CREATION } + RecentDayStatus.CHECKED,
            item.recentDays.map { it.status })
    }

    @Test
    fun onHabitChecked_updatesRecentDayAndStreakOptimistically() = runTest {
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
        assertEquals(RecentDayStatus.CHECKED, state.items.single().recentDays.last().status)
        assertEquals(2, state.items.single().streak)
        persistGate.complete(Unit)
    }

    @Test
    fun onHabitUnchecked_keepsYesterdayInRecentDaysWhileStreakResets() = runTest {
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
        assertEquals(RecentDayStatus.CHECKED, state.items.single().recentDays[5].status)
        assertEquals(RecentDayStatus.NOT_CHECKED, state.items.single().recentDays[6].status)
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
            DashboardUiEvent.ShowResourceMessage(R.string.check_in_conflict),
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
        assertEquals(DashboardUiEvent.ShowResourceMessage(R.string.check_in_date_changed), event.await())
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
        val today = LocalDate.of(2026, 9, 19)
        val storedHabitDao = TestHabitDao(listOf(habit(id = 7, name = "阅读")))
        val habitDao = ThrowingDeleteHabitDao(storedHabitDao)
        val recordDao = TestRecordDao(listOf(record(7, today, isDone = true)))
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(today)
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.deleteHabit(7)

        assertEquals(
            DashboardUiEvent.ShowResourceMessage(R.string.habit_delete_failed),
            event.await()
        )
        assertNotNull(storedHabitDao.getHabitById(7))
        assertNotNull(recordDao.getRecordByDate(7, today.toEpochDay()))
    }

    @Test
    fun deleteHabit_whenDaoDeletesHabit_emitsSuccessAndRefreshesDashboard() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = TestHabitDao(listOf(habit(id = 7, name = "阅读")))
        val recordDao = TestRecordDao(emptyList())
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(today)
        )
        viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
            .first { it.items.singleOrNull()?.id == 7 }
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.deleteHabit(7)

        assertEquals(
            DashboardUiEvent.ShowResourceMessage(R.string.habit_delete_success),
            event.await()
        )
        assertEquals(
            emptyList<HabitItemUiState>(),
            viewModel.uiState.filterIsInstance<DashboardUiState.Success>()
                .first { it.items.isEmpty() }.items
        )
    }

    @Test
    fun deleteHabit_duplicateRequestReportsSuccessOnlyForDeletedRow() = runTest {
        val habitDao = TestHabitDao(listOf(habit(id = 7, name = "阅读")))
        val recordDao = TestRecordDao(emptyList())
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(LocalDate.of(2026, 9, 19))
        )
        val events = mutableListOf<DashboardUiEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { events += it }
        }

        viewModel.deleteHabit(7)
        viewModel.deleteHabit(7)
        runCurrent()

        assertEquals(null, habitDao.getHabitById(7))
        assertEquals(
            listOf(DashboardUiEvent.ShowResourceMessage(R.string.habit_delete_success)),
            events
        )
    }

    @Test
    fun deleteHabit_whenCancelled_keepsRowsAndDoesNotEmitFailure() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val storedHabitDao = TestHabitDao(listOf(habit(id = 7, name = "阅读")))
        val habitDao = object : HabitDao by storedHabitDao {
            override suspend fun deleteHabitById(habitId: Int): Int {
                throw CancellationException("cancelled delete")
            }
        }
        val recordDao = TestRecordDao(listOf(record(7, today, isDone = true)))
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(today)
        )
        val events = mutableListOf<DashboardUiEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { events += it }
        }

        viewModel.deleteHabit(7)
        runCurrent()

        assertEquals(emptyList<DashboardUiEvent>(), events)
        assertNotNull(storedHabitDao.getHabitById(7))
        assertNotNull(recordDao.getRecordByDate(7, today.toEpochDay()))
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

        assertEquals(R.string.dashboard_load_failed, error.messageResId)

        viewModel.retry()

        val success = viewModel.uiState
            .filterIsInstance<DashboardUiState.Success>()
            .first { state -> state.items.singleOrNull()?.name == "阅读" }

        assertEquals("阅读", success.items.single().name)
        assertEquals(2, habitDao.subscriptionCount)
    }

    @Test
    fun cancelledHabitFlowDoesNotEmitOrdinaryLoadError() = runTest {
        val sourceStarted = CompletableDeferred<Unit>()
        val sourceCancelled = CompletableDeferred<Unit>()
        val habitDao = object : HabitDao by TestHabitDao(emptyList()) {
            override fun getAllHabits(): Flow<List<HabitEntity>> = flow {
                sourceStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    sourceCancelled.complete(Unit)
                }
            }
        }
        val recordDao = TestRecordDao(emptyList())
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = acceptedUseCase(),
            todaySource = flowOf(LocalDate.of(2026, 9, 19))
        )
        val states = mutableListOf<DashboardUiState>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect { states += it }
        }

        runCurrent()
        withTimeout(2_000L) { sourceStarted.await() }
        collector.cancel()
        // stateIn 的 WhileSubscribed 保留五秒；实际取消后 catch 不应生成 Error。
        advanceTimeBy(5_100L)
        runCurrent()
        withTimeout(2_000L) { sourceCancelled.await() }

        assertFalse(states.any { it is DashboardUiState.Error })
        assertFalse(viewModel.uiState.value is DashboardUiState.Error)
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

    private class ThrowingDeleteHabitDao(delegate: HabitDao) : HabitDao by delegate {
        override suspend fun deleteHabitById(habitId: Int): Int {
            error("database unavailable")
        }
    }
}
