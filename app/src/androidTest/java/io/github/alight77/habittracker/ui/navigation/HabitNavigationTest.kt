package io.github.alight77.habittracker.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.activity.ComponentActivity
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.MainActivity
import io.github.alight77.habittracker.HabitTrackerViewModelFactory
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.core.time.TodayDateSource
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.feature.dashboard.DashboardViewModel
import io.github.alight77.habittracker.feature.stats.StatsViewModel
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HabitNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun topBackDiscardsAddDraftBeforeNextEntry() {
        val addLabel = composeRule.activity.getString(R.string.add_habit)
        val backLabel = composeRule.activity.getString(R.string.back)
        val decreaseLabel = composeRule.activity.getString(R.string.decrease_weekly_goal)
        val emptyName = composeRule.activity.getString(R.string.habit_preview_empty_name)
        val defaultTarget = composeRule.activity.getString(R.string.habit_target_summary, 7)

        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_name_input").performTextInput("RC02-Draft")
        composeRule.onNodeWithContentDescription(decreaseLabel).performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals("RC02-Draft")
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()

        composeRule.onNodeWithContentDescription(backLabel).performClick()
        composeRule.onNodeWithContentDescription(addLabel).performClick()

        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals(emptyName)
        composeRule.onNodeWithTag("habit_preview_target").assertTextEquals(defaultTarget)
        composeRule.onNodeWithTag("habit_preview_icon_general").assertExists()

        composeRule.onNodeWithContentDescription(backLabel).performClick()
        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals(emptyName)
        composeRule.onNodeWithTag("habit_name_input").performTextInput("RC02-Second")
        composeRule.onNodeWithContentDescription(backLabel).performClick()
        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals(emptyName)
    }

    @Test
    fun systemBackDiscardsAddDraftBeforeNextEntry() {
        val addLabel = composeRule.activity.getString(R.string.add_habit)
        val decreaseLabel = composeRule.activity.getString(R.string.decrease_weekly_goal)
        val emptyName = composeRule.activity.getString(R.string.habit_preview_empty_name)

        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_name_suggestion_0").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(decreaseLabel).performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()

        pressBack()
        composeRule.onNodeWithTag("nav_today").assertIsSelected()
        composeRule.onNodeWithContentDescription(addLabel).performClick()

        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals(emptyName)
        composeRule.onNodeWithTag("habit_preview_target")
            .assertTextEquals(composeRule.activity.getString(R.string.habit_target_summary, 7))
        composeRule.onNodeWithTag("habit_preview_icon_general").assertExists()
    }

    @Test
    fun recreationKeepsDraftButLeavingRouteDiscardsIt() {
        val addLabel = composeRule.activity.getString(R.string.add_habit)
        val backLabel = composeRule.activity.getString(R.string.back)
        val decreaseLabel = composeRule.activity.getString(R.string.decrease_weekly_goal)

        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_name_suggestion_0").performScrollTo().performClick()
        val draftName = composeRule.activity.resources.getStringArray(R.array.habit_name_suggestions)[0]
        composeRule.onNodeWithContentDescription(decreaseLabel).performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals(draftName)
        composeRule.onNodeWithTag("habit_preview_target")
            .assertTextEquals(composeRule.activity.getString(R.string.habit_target_summary, 6))
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()

        composeRule.onNodeWithContentDescription(backLabel).performClick()
        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_preview_name")
            .assertTextEquals(composeRule.activity.getString(R.string.habit_preview_empty_name))
    }

    @Test
    fun tabSwitchesKeepTodayAsBackDestination() {
        composeRule.onNodeWithTag("nav_today").assertIsSelected()

        repeat(4) {
            composeRule.onNodeWithTag("nav_today").performClick()
            composeRule.onNodeWithTag("nav_stats").performClick()
            composeRule.onNodeWithTag("nav_stats").assertIsSelected()
            composeRule.onNodeWithTag("nav_stats").performClick()
            composeRule.onNodeWithTag("nav_today").performClick()
            composeRule.onNodeWithTag("nav_today").assertIsSelected()
        }

        composeRule.onNodeWithTag("nav_stats").performClick()
        pressBack()
        composeRule.onNodeWithTag("nav_today").assertIsSelected()
    }

    @Test
    fun addPageHidesTabsAndBackRestoresToday() {
        val addLabel = composeRule.activity.getString(R.string.add_habit)
        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithText(addLabel).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_today").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_stats").assertDoesNotExist()

        pressBack()
        composeRule.onNodeWithTag("nav_today").assertIsSelected()
    }

    @Test
    fun recreationRestoresSelectedTabAndBackDestination() {
        composeRule.onNodeWithTag("nav_stats").performClick()
        composeRule.onNodeWithTag("nav_stats").assertIsSelected()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("nav_stats").assertIsSelected()
        pressBack()
        composeRule.onNodeWithTag("nav_today").assertIsSelected()
    }
}

@RunWith(AndroidJUnit4::class)
class HabitAddSaveNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: HabitDatabase

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun backDuringPendingInsertStaysOnFormUntilSaveCompletes() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        val insertStarted = CompletableDeferred<Unit>()
        val allowInsert = CompletableDeferred<Unit>()
        val realHabitDao = database.habitDao()
        val controlledHabitDao = object : HabitDao by realHabitDao {
            override suspend fun insertHabit(habit: HabitEntity) {
                insertStarted.complete(Unit)
                allowInsert.await()
                realHabitDao.insertHabit(habit)
            }
        }
        val repository = HabitRepository(controlledHabitDao, database.recordDao())
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
            observeSourceChecked = { flowOf(false) }
        )
        val todaySource = flowOf(LocalDate.now())
        val dashboardViewModel = DashboardViewModel(
            habitDao = controlledHabitDao,
            recordDao = database.recordDao(),
            repository = repository,
            setTodayHabitChecked = useCase,
            todaySource = todaySource
        )
        val statsViewModel = StatsViewModel(
            habitDao = controlledHabitDao,
            recordDao = database.recordDao(),
            todaySource = todaySource
        )
        val addHabitViewModelFactory = HabitTrackerViewModelFactory(
            repository = repository,
            habitDao = controlledHabitDao,
            recordDao = database.recordDao(),
            setTodayHabitChecked = useCase,
            todayDateSource = TodayDateSource(composeRule.activity.applicationContext)
        )

        composeRule.setContent {
            HabitTrackerTheme {
                HabitNavHost(
                    navController = rememberNavController(),
                    dashboardViewModel = dashboardViewModel,
                    addHabitViewModelFactory = addHabitViewModelFactory,
                    statsViewModel = statsViewModel,
                    repository = repository
                )
            }
        }

        val addLabel = composeRule.activity.getString(R.string.add_habit)
        val backLabel = composeRule.activity.getString(R.string.back)
        val saveLabel = composeRule.activity.getString(R.string.save_habit)
        composeRule.onNodeWithContentDescription(addLabel).performClick()
        composeRule.onNodeWithTag("habit_name_suggestion_0").performScrollTo().performClick()
        composeRule.onNodeWithText(saveLabel).performScrollTo().performClick()
        runBlocking { withTimeout(5_000L) { insertStarted.await() } }

        try {
            // 写入尚未结束时，顶部返回和系统 Back 都不能清除持有保存协程的导航会话。
            composeRule.onNodeWithContentDescription(backLabel).performClick()
            composeRule.onNodeWithTag("habit_name_input").assertExists()
            pressBack()
            composeRule.onNodeWithTag("habit_name_input").assertExists()
            check(runBlocking { database.habitDao().getAllHabits().first().isEmpty() })
        } finally {
            allowInsert.complete(Unit)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("nav_today").fetchSemanticsNodes().isNotEmpty()
        }
        check(runBlocking { database.habitDao().getAllHabits().first().size == 1 })
    }
}

@RunWith(AndroidJUnit4::class)
class HabitEditNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: HabitDatabase

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun editPageHidesTabsAndBackRestoresToday() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "导航测试习惯", description = "", targetPerWeek = 3, createdAt = 100L)
            )
        }
        val repository = HabitRepository(database.habitDao(), database.recordDao())
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
            observeSourceChecked = { flowOf(false) }
        )
        val todaySource = flowOf(LocalDate.now())
        val dashboardViewModel = DashboardViewModel(
            habitDao = database.habitDao(),
            recordDao = database.recordDao(),
            repository = repository,
            setTodayHabitChecked = useCase,
            todaySource = todaySource
        )
        val statsViewModel = StatsViewModel(
            habitDao = database.habitDao(),
            recordDao = database.recordDao(),
            todaySource = todaySource
        )
        val addHabitViewModelFactory = HabitTrackerViewModelFactory(
            repository = repository,
            habitDao = database.habitDao(),
            recordDao = database.recordDao(),
            setTodayHabitChecked = useCase,
            todayDateSource = TodayDateSource(composeRule.activity.applicationContext)
        )

        composeRule.setContent {
            HabitTrackerTheme {
                HabitNavHost(
                    navController = rememberNavController(),
                    dashboardViewModel = dashboardViewModel,
                    addHabitViewModelFactory = addHabitViewModelFactory,
                    statsViewModel = statsViewModel,
                    repository = repository
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.habit_more_actions, "导航测试习惯")
        ).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.edit)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.edit_habit)).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_today").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_stats").assertDoesNotExist()

        pressBack()
        composeRule.onNodeWithTag("nav_today").assertIsSelected()
    }
}
