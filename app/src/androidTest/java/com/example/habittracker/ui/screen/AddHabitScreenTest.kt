package com.example.habittracker.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habittracker.data.local.HabitDatabase
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.viewmodel.HabitViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AddHabitScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: HabitDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveHabit_withBlankName_showsValidationError() {
        composeRule.setContent {
            AddHabitScreen(
                viewModel = viewModel(),
                onBackClick = {}
            )
        }

        composeRule.onNodeWithText("保存").performClick()

        composeRule.onNodeWithText("请输入习惯名称").assertIsDisplayed()
    }

    @Test
    fun saveHabit_withValidName_persistsHabitAndNavigatesBack() {
        val backClickCount = AtomicInteger(0)
        composeRule.setContent {
            AddHabitScreen(
                viewModel = viewModel(),
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("阅读")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }

        assertEquals(1, backClickCount.get())
        assertEquals(
            "阅读",
            runBlocking { database.habitDao().getAllHabits().first().single().name }
        )
    }

    @Test
    fun saveHabit_withSelectedTarget_persistsTargetAndDisablesUpperBound() {
        val backClickCount = AtomicInteger(0)
        composeRule.setContent {
            AddHabitScreen(
                viewModel = viewModel(),
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithText("＋").assertIsNotEnabled()
        repeat(2) { composeRule.onNodeWithText("−").performClick() }
        composeRule.onNodeWithText("5 次 / 周").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction()).performTextInput("阅读")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }

        assertEquals(
            5,
            runBlocking { database.habitDao().getAllHabits().first().single().targetPerWeek }
        )
    }

    @Test
    fun backButton_invokesOnBackClick() {
        val backClickCount = AtomicInteger(0)
        composeRule.setContent {
            AddHabitScreen(
                viewModel = viewModel(),
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithContentDescription("返回").performClick()

        assertEquals(1, backClickCount.get())
    }

    private fun viewModel(): HabitViewModel {
        return HabitViewModel(
            HabitRepository(database.habitDao(), database.recordDao())
        )
    }
}
