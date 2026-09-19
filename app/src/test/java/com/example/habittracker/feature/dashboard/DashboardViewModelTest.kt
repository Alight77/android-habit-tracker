package com.example.habittracker.feature.dashboard

import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.testutil.MainDispatcherRule
import com.example.habittracker.testutil.TestHabitDao
import com.example.habittracker.testutil.TestRecordDao
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
        val viewModel = DashboardViewModel(
            habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读"))),
            recordDao = TestRecordDao(
                listOf(
                    record(habitId = 1, date = today, isDone = true),
                    record(habitId = 1, date = today.minusDays(1), isDone = true)
                )
            ),
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
            habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读"))),
            recordDao = TestRecordDao(emptyList()),
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
}
