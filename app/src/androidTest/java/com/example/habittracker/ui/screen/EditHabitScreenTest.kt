package com.example.habittracker.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habittracker.data.local.HabitDatabase
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.feature.edit.EditHabitViewModel
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
class EditHabitScreenTest {

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
    fun saveHabit_updatesNameAndTargetThenNavigatesBack() {
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(
                    name = "阅读",
                    description = "保留的描述",
                    targetPerWeek = 3,
                    createdAt = 100L
                )
            )
        }
        val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
        val backClickCount = AtomicInteger(0)
        val viewModel = EditHabitViewModel(
            repository = HabitRepository(database.habitDao(), database.recordDao()),
            habitId = habitId
        )

        composeRule.setContent {
            EditHabitScreen(
                viewModel = viewModel,
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("减少每周目标次数").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("增加每周目标次数").assertIsDisplayed()
        composeRule.onNodeWithText("3 次 / 周").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("晨读")
        repeat(2) { composeRule.onNodeWithText("＋").performClick() }
        composeRule.onNodeWithText("5 次 / 周").assertIsDisplayed()
        composeRule.onNodeWithText("保存修改").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }

        assertEquals(
            HabitEntity(
                id = habitId,
                name = "晨读",
                description = "保留的描述",
                targetPerWeek = 5,
                createdAt = 100L
            ),
            runBlocking { database.habitDao().getHabitById(habitId) }
        )
    }
}
