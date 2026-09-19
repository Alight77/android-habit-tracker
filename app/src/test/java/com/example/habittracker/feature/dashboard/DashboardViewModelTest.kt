package com.example.habittracker.feature.dashboard

import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.testutil.MainDispatcherRule
import com.example.habittracker.testutil.TestHabitDao
import com.example.habittracker.testutil.TestRecordDao
import com.example.habittracker.testutil.ThrowOnceHabitDao
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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
                streak = 2
            ),
            state.items.single()
        )
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
            todaySource = flowOf(today)
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) { viewModel.events.take(1).toList().single() }
        }

        viewModel.onHabitChecked(habitId = 1, targetChecked = true)

        assertEquals(
            DashboardUiEvent.ShowMessage("打卡状态冲突，请重试"),
            event.await()
        )
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

    private fun habit(id: Int, name: String): HabitEntity {
        return HabitEntity(
            id = id,
            name = name,
            description = "",
            targetPerWeek = 7,
            createdAt = 0
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
