package io.github.alight77.habittracker.feature.stats

import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.testutil.MainDispatcherRule
import io.github.alight77.habittracker.testutil.TestHabitDao
import io.github.alight77.habittracker.testutil.TestRecordDao
import io.github.alight77.habittracker.testutil.ThrowOnceHabitDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
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
    fun uiState_movesSevenDayWindowWhenDateChanges() = runTest {
        val previousDate = LocalDate.of(2026, 9, 19)
        val currentDate = previousDate.plusDays(1)
        val todaySource = MutableStateFlow(previousDate)
        val viewModel = StatsViewModel(
            habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读"))),
            recordDao = TestRecordDao(
                listOf(
                    record(1, previousDate.minusDays(6), isDone = true),
                    record(1, previousDate, isDone = true),
                    record(1, currentDate, isDone = true)
                )
            ),
            todaySource = todaySource
        )

        val previous = viewModel.uiState.filterIsInstance<StatsUiState.Success>()
            .first { it.summary.recentSevenDayCounts.last().date == previousDate }
        assertEquals(1, previous.summary.recentSevenDayCounts.first().completedHabits)
        assertEquals(1, previous.summary.recentSevenDayCounts.last().completedHabits)

        todaySource.value = currentDate
        val current = viewModel.uiState.filterIsInstance<StatsUiState.Success>()
            .first { it.summary.recentSevenDayCounts.last().date == currentDate }
        assertEquals(0, current.summary.recentSevenDayCounts.first().completedHabits)
        assertEquals(1, current.summary.recentSevenDayCounts[5].completedHabits)
        assertEquals(1, current.summary.recentSevenDayCounts.last().completedHabits)
    }

    @Test
    fun uiState_updatesTodayCountWhenDateChanges() = runTest {
        val previousDate = LocalDate.of(2026, 9, 19)
        val currentDate = previousDate.plusDays(1)
        val todaySource = MutableStateFlow(previousDate)
        val viewModel = StatsViewModel(
            habitDao = TestHabitDao(listOf(habit(id = 1, name = "阅读"))),
            recordDao = TestRecordDao(listOf(record(1, currentDate, isDone = true))),
            todaySource = todaySource
        )

        val previous = viewModel.uiState.filterIsInstance<StatsUiState.Success>()
            .first { it.summary.todayDoneCount == 0 }
        assertEquals(0, previous.summary.todayDoneCount)

        todaySource.value = currentDate
        val current = viewModel.uiState.filterIsInstance<StatsUiState.Success>()
            .first { it.summary.todayDoneCount == 1 }
        assertEquals(1, current.summary.todayDoneCount)
    }

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
                recentSevenDayGoalPercent = 14,
                longestStreak = 1,
                recentSevenDayCounts = (6L downTo 0L).map { daysAgo ->
                    val date = today.minusDays(daysAgo)
                    DailyCheckInCount(date, if (daysAgo <= 1L) 1 else 0)
                }
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

        assertEquals(StatsUiState.Error(R.string.stats_load_failed), error)

        viewModel.retry()

        val success = viewModel.uiState
            .filterIsInstance<StatsUiState.Success>()
            .first { state -> state.summary.totalHabits == 1 }

        assertEquals(1, success.summary.totalHabits)
        assertEquals(2, habitDao.subscriptionCount)
    }

    @Test
    fun repeatedFailuresKeepStableErrorIdUntilRetrySucceeds() = runTest {
        val habits = TestHabitDao(listOf(habit(id = 1, name = "阅读")))
        var subscriptionCount = 0
        val failingDao = object : HabitDao by habits {
            override fun getAllHabits(): Flow<List<HabitEntity>> = flow {
                subscriptionCount += 1
                if (subscriptionCount <= 2) error("habit source unavailable")
                emitAll(habits.getAllHabits())
            }
        }
        val viewModel = StatsViewModel(
            habitDao = failingDao,
            recordDao = TestRecordDao(emptyList()),
            todaySource = flowOf(LocalDate.of(2026, 9, 19))
        )
        backgroundScope.launch { viewModel.uiState.collect() }

        runCurrent()
        assertEquals(StatsUiState.Error(R.string.stats_load_failed), viewModel.uiState.value)
        assertEquals(1, subscriptionCount)

        viewModel.retry()
        runCurrent()
        assertEquals(StatsUiState.Error(R.string.stats_load_failed), viewModel.uiState.value)
        assertEquals(2, subscriptionCount)

        viewModel.retry()
        runCurrent()
        val success = viewModel.uiState.value as StatsUiState.Success
        assertEquals(1, success.summary.totalHabits)
        assertEquals(3, subscriptionCount)
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
