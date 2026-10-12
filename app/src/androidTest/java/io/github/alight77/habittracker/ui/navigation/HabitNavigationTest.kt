package io.github.alight77.habittracker.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.MainActivity
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.feature.addhabit.AddHabitViewModel
import io.github.alight77.habittracker.feature.dashboard.DashboardViewModel
import io.github.alight77.habittracker.feature.stats.StatsViewModel
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
        composeRule.onNodeWithContentDescription("新增习惯").performClick()
        composeRule.onNodeWithText("新增习惯").assertIsDisplayed()
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

        composeRule.setContent {
            HabitTrackerTheme {
                HabitNavHost(
                    navController = rememberNavController(),
                    dashboardViewModel = dashboardViewModel,
                    addHabitViewModel = AddHabitViewModel(repository),
                    statsViewModel = statsViewModel,
                    repository = repository
                )
            }
        }

        composeRule.onNodeWithContentDescription("更多操作：导航测试习惯").performClick()
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.onNodeWithText("编辑习惯").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_today").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_stats").assertDoesNotExist()

        pressBack()
        composeRule.onNodeWithTag("nav_today").assertIsSelected()
    }
}
