package com.example.habittracker.feature.stats

import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.local.RecordEntity
import com.example.habittracker.testutil.MainDispatcherRule
import com.example.habittracker.testutil.TestHabitDao
import com.example.habittracker.testutil.TestRecordDao
import com.example.habittracker.testutil.ThrowOnceHabitDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_calculatesSummaryFromDaoFlows() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val viewModel = StatsViewModel(
            habitDao = TestHabitDao(
                listOf(
                    habit(id = 1, name = "阅读"),
                    habit(id = 2, name = "运动")
                )
            ),
            recordDao = TestRecordDao(
                listOf(
                    record(habitId = 1, date = today, isDone = true),
                    record(habitId = 2, date = today.minusDays(1), isDone = true),
                    record(habitId = 2, date = today.minusDays(8), isDone = true)
                )
            ),
            todaySource = flowOf(today)
        )

        val state = viewModel.uiState
            .filterIsInstance<StatsUiState.Success>()
            .first { success -> success.summary.totalDoneCount == 3 }

        assertEquals(
            StatsSummary(
                totalHabits = 2,
                todayDoneCount = 1,
                totalDoneCount = 3,
                recentSevenDayCompletionPercent = 14,
                bestCurrentStreak = 1
            ),
            state.summary
        )
    }

    @Test
    fun retry_afterHabitFlowFails_resubscribesAndEmitsSuccess() = runTest {
        val today = LocalDate.of(2026, 9, 19)
        val habitDao = ThrowOnceHabitDao(listOf(habit(id = 1, name = "阅读")))
        val viewModel = StatsViewModel(
            habitDao = habitDao,
            recordDao = TestRecordDao(emptyList()),
            todaySource = flowOf(today)
        )

        val error = viewModel.uiState
            .filterIsInstance<StatsUiState.Error>()
            .first()

        assertEquals("加载统计数据失败，请重试", error.message)

        viewModel.retry()

        val success = viewModel.uiState
            .filterIsInstance<StatsUiState.Success>()
            .first { state -> state.summary.totalHabits == 1 }

        assertEquals(1, success.summary.totalHabits)
        assertEquals(2, habitDao.subscriptionCount)
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
