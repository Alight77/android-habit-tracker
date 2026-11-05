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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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

    @Test
    fun defaultSelection_usesNewestCreationAndIdForTies() = runTest {
        val today = LocalDate.of(2026, 9, 21)
        val habits = TestHabitDao(listOf(
            habit(8, "同日较小 ID", createdAt = 2),
            habit(1, "较早", createdAt = 1),
            habit(9, "同日较大 ID", createdAt = 2)
        ))
        val viewModel = StatsViewModel(habits, TestRecordDao(emptyList()), flowOf(today))
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        val state = viewModel.successState()
        assertEquals(listOf(9, 8, 1), state.availableHabits.map { it.id })
        assertEquals(9, state.selectedHabitId)
        assertEquals(5, state.weeklyTrend.size)

        habits.deleteHabitById(9)
        runCurrent()
        assertEquals(8, viewModel.successState().selectedHabitId)
    }

    @Test
    fun selectingHabits_updatesTrendWithoutRecalculatingSummaryOrWritingData() = runTest {
        val today = LocalDate.of(2026, 9, 21)
        val habits = TestHabitDao(listOf(habit(1, "阅读"), habit(2, "运动")))
        val records = TestRecordDao(listOf(record(1, today, true), record(2, today, false)))
        val viewModel = StatsViewModel(habits, records, flowOf(today))
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        val before = viewModel.successState()
        assertEquals(2, before.selectedHabitId)
        assertEquals(0, before.weeklyTrend.last().completedDays)

        viewModel.onHabitSelected(1)
        runCurrent()
        val selected = viewModel.successState()
        assertEquals(1, selected.selectedHabitId)
        assertEquals(1, selected.weeklyTrend.last().completedDays)
        assertSame(before.summary, selected.summary)
        assertEquals(true, records.getRecordByDate(1, today.toEpochDay())?.isDone)

        viewModel.onHabitSelected(2)
        viewModel.onHabitSelected(1)
        runCurrent()
        assertEquals(1, viewModel.successState().selectedHabitId)
        viewModel.onHabitSelected(999)
        runCurrent()
        assertEquals(1, viewModel.successState().selectedHabitId)
    }

    @Test
    fun addingRenamingAndDeletingHabits_preservesOrFallsBackSelection() = runTest {
        val today = LocalDate.of(2026, 9, 21)
        val habits = TestHabitDao(listOf(habit(1, "阅读", 1), habit(2, "运动", 2)))
        val viewModel = StatsViewModel(habits, TestRecordDao(emptyList()), flowOf(today))
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        viewModel.onHabitSelected(1)
        runCurrent()

        habits.insertHabit(habit(3, "冥想", 3))
        runCurrent()
        assertEquals(1, viewModel.successState().selectedHabitId)

        habits.updateHabit(habit(1, "阅读新版", 1))
        runCurrent()
        val renamed = viewModel.successState()
        assertEquals(1, renamed.selectedHabitId)
        assertEquals("阅读新版", renamed.availableHabits.first { it.id == 1 }.name)

        habits.deleteHabitById(2)
        runCurrent()
        assertEquals(1, viewModel.successState().selectedHabitId)

        habits.deleteHabitById(1)
        runCurrent()
        assertEquals(3, viewModel.successState().selectedHabitId)

        habits.deleteHabitById(3)
        runCurrent()
        val empty = viewModel.successState()
        assertEquals(null, empty.selectedHabitId)
        assertTrue(empty.availableHabits.isEmpty())
        assertTrue(empty.weeklyTrend.isEmpty())
        assertEquals(0, empty.summary.totalHabits)

        habits.insertHabit(habit(4, "新习惯", 4))
        runCurrent()
        assertEquals(4, viewModel.successState().selectedHabitId)
    }

    @Test
    fun checkingAndUndoingToday_updatesSelectedTrend() = runTest {
        val today = LocalDate.of(2026, 9, 21)
        val records = TestRecordDao(emptyList())
        val viewModel = StatsViewModel(
            TestHabitDao(listOf(habit(1, "阅读"))), records, flowOf(today)
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(0, viewModel.successState().weeklyTrend.last().completedDays)

        records.setRecordChecked(1, today.toEpochDay(), true)
        runCurrent()
        assertEquals(1, viewModel.successState().weeklyTrend.last().completedDays)

        records.setRecordChecked(1, today.toEpochDay(), false)
        runCurrent()
        assertEquals(0, viewModel.successState().weeklyTrend.last().completedDays)
    }

    @Test
    fun createdAtUsesSystemZoneAndExcludesEarlierCheckIns() = runTest {
        val today = LocalDate.of(2026, 9, 23)
        val createdDate = today.minusDays(1)
        val createdAt = createdDate.atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val viewModel = StatsViewModel(
            TestHabitDao(listOf(habit(1, "阅读", createdAt))),
            TestRecordDao(listOf(
                record(1, createdDate.minusDays(1), true),
                record(1, createdDate, true)
            )),
            flowOf(today)
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()

        assertEquals(1, viewModel.successState().weeklyTrend.last().completedDays)
    }

    @Test
    fun dateCrossingMonday_movesFiveWeekWindow() = runTest {
        val sunday = LocalDate.of(2026, 9, 20)
        val monday = sunday.plusDays(1)
        val todaySource = MutableStateFlow(sunday)
        val viewModel = StatsViewModel(
            TestHabitDao(listOf(habit(1, "阅读"))),
            TestRecordDao(listOf(record(1, sunday, true), record(1, monday, true))),
            todaySource
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        val previous = viewModel.successState().weeklyTrend
        assertEquals(sunday.minusDays(6), previous.last().weekStart)
        assertEquals(1, previous.last().completedDays)

        todaySource.value = monday
        runCurrent()
        val current = viewModel.successState().weeklyTrend
        assertEquals(previous[1].weekStart, current.first().weekStart)
        assertEquals(monday, current.last().weekStart)
        assertEquals(1, current[3].completedDays)
        assertEquals(1, current.last().completedDays)
        assertEquals(monday, current.last().observedThrough)
    }

    @Test
    fun selectionSurvivesWhileSubscribedStopAndRestart() = runTest {
        val today = LocalDate.of(2026, 9, 21)
        val habits = TestHabitDao(listOf(habit(1, "阅读", 1), habit(2, "运动", 2)))
        val viewModel = StatsViewModel(habits, TestRecordDao(emptyList()), flowOf(today))
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        viewModel.onHabitSelected(1)
        runCurrent()
        collector.cancel()
        advanceTimeBy(5_001)
        runCurrent()

        habits.insertHabit(habit(3, "冥想", 3))
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(1, viewModel.successState().selectedHabitId)
        assertEquals(listOf(3, 2, 1), viewModel.successState().availableHabits.map { it.id })
    }

    private fun StatsViewModel.successState(): StatsUiState.Success {
        return uiState.value as StatsUiState.Success
    }

    private fun habit(id: Int, name: String, createdAt: Long = 0): HabitEntity {
        return HabitEntity(
            id = id,
            name = name,
            description = "",
            targetPerWeek = 7,
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
}
