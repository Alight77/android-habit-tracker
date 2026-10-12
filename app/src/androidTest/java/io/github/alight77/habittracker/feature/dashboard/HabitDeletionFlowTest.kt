package io.github.alight77.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.feature.stats.StatsUiState
import io.github.alight77.habittracker.feature.stats.StatsViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HabitDeletionFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: HabitDatabase

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun cancelPreservesRowsThenConfirmCascadesAndRefreshesDashboardAndStats() {
        val today = LocalDate.now()
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        runBlocking {
            database.habitDao().insertHabit(habit(7, "阅读"))
            database.habitDao().insertHabit(habit(8, "跑步"))
            database.recordDao().setRecordChecked(7, today.toEpochDay(), true)
            database.recordDao().setRecordChecked(8, today.toEpochDay(), true)
        }
        val repository = HabitRepository(database.habitDao(), database.recordDao())
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
            observeSourceChecked = { flowOf(false) }
        )
        val dashboard = DashboardViewModel(
            habitDao = database.habitDao(),
            recordDao = database.recordDao(),
            repository = repository,
            setTodayHabitChecked = useCase,
            todaySource = flowOf(today),
            currentDate = { today }
        )
        val stats = StatsViewModel(
            habitDao = database.habitDao(),
            recordDao = database.recordDao(),
            todaySource = flowOf(today)
        )
        composeRule.setContent {
            DashboardScreen(
                viewModel = dashboard,
                onAddClick = {},
                onEditClick = {}
            )
        }
        composeRule.onNodeWithContentDescription("更多操作：阅读").assertExists()
        runBlocking {
            val initial = withTimeout(5_000L) {
                stats.uiState.filterIsInstance<StatsUiState.Success>()
                    .first { it.summary.totalHabits == 2 }.summary
            }
            assertEquals(2, initial.totalDoneCount)
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("取消").performClick()
        runBlocking {
            assertNotNull(database.habitDao().getHabitById(7))
            assertNotNull(database.recordDao().getRecordByDate(7, today.toEpochDay()))
            assertEquals(2, database.habitDao().getAllHabits().first().size)
            assertEquals(2, database.recordDao().getAllRecords().first().size)
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            val state = dashboard.uiState.value as? DashboardUiState.Success
            state?.items?.map { it.id } == listOf(8)
        }
        composeRule.onNodeWithContentDescription("更多操作：跑步").assertExists()
        runBlocking {
            assertNull(database.habitDao().getHabitById(7))
            assertNull(database.recordDao().getRecordByDate(7, today.toEpochDay()))
            assertNotNull(database.habitDao().getHabitById(8))
            assertNotNull(database.recordDao().getRecordByDate(8, today.toEpochDay()))
            val summary = withTimeout(5_000L) {
                stats.uiState.filterIsInstance<StatsUiState.Success>()
                    .first { it.summary.totalHabits == 1 }.summary
            }
            assertEquals(1, summary.todayDoneCount)
            assertEquals(1, summary.totalDoneCount)
        }
    }

    @Test
    fun failedDeleteKeepsRoomRowsAndShowsDashboardError() {
        val today = LocalDate.now()
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        runBlocking {
            database.habitDao().insertHabit(habit(7, "阅读"))
            database.recordDao().setRecordChecked(7, today.toEpochDay(), true)
        }
        val failingHabitDao = object : HabitDao by database.habitDao() {
            override suspend fun deleteHabitById(habitId: Int): Int {
                error("controlled delete failure")
            }
        }
        val dashboard = DashboardViewModel(
            habitDao = failingHabitDao,
            recordDao = database.recordDao(),
            repository = HabitRepository(failingHabitDao, database.recordDao()),
            setTodayHabitChecked = SetTodayHabitCheckedUseCase(
                applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
                observeSourceChecked = { flowOf(false) }
            ),
            todaySource = flowOf(today),
            currentDate = { today }
        )
        composeRule.setContent {
            DashboardScreen(
                viewModel = dashboard,
                onAddClick = {},
                onEditClick = {}
            )
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("永久删除").performClick()
        composeRule.onNodeWithText("删除习惯失败，请稍后重试").assertIsDisplayed()
        runBlocking {
            assertNotNull(database.habitDao().getHabitById(7))
            assertNotNull(database.recordDao().getRecordByDate(7, today.toEpochDay()))
        }
        composeRule.onNodeWithContentDescription("更多操作：阅读").assertExists()
    }

    private fun habit(id: Int, name: String) = HabitEntity(
        id = id,
        name = name,
        description = "",
        targetPerWeek = 7,
        createdAt = 0L
    )
}
