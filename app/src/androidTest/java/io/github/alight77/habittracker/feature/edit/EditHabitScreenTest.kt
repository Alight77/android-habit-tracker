package io.github.alight77.habittracker.feature.edit

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
        composeRule.onNodeWithText("持续让好习惯生长").assertIsDisplayed()
        composeRule.onNodeWithText("试试这些名称").assertDoesNotExist()
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals("阅读")
        composeRule.onNodeWithContentDescription("减少每周目标次数").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("增加每周目标次数").assertIsDisplayed()
        composeRule.onNodeWithText("3 次 / 周").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("晨读")
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals("晨读")
        repeat(2) { composeRule.onNodeWithText("＋").performScrollTo().performClick() }
        // 大字号加键盘时目标值可位于视口下方；验证它可滚动到达后再保存。
        composeRule.onNodeWithText("5 次 / 周").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("habit_preview_target").assertTextEquals("每周目标 5 次")
        composeRule.onNodeWithText("保存修改").performScrollTo().performClick()
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

    @Test
    fun savedIcon_manualSelectionAndRecommendation_updateRoomOnlyOnSave() {
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "阅读", description = "", targetPerWeek = 3, createdAt = 100L, iconKey = "reading")
            )
        }
        val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
        val backClickCount = AtomicInteger(0)
        val stableViewModel = EditHabitViewModel(HabitRepository(database.habitDao(), database.recordDao()), habitId)
        composeRule.setContent {
            EditHabitScreen(stableViewModel, onBackClick = { backClickCount.incrementAndGet() })
        }
        composeRule.onNodeWithTag("habit_preview_icon_reading").assertExists()
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_reading").assertIsSelected()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()
        assertEquals("reading", runBlocking { database.habitDao().getHabitById(habitId)?.iconKey })
        stableViewModel.onNameChanged("晨跑")
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()
        composeRule.onNodeWithTag("habit_icon_action").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_icon_exercise").assertExists()
        stableViewModel.onNameChanged("健康餐")
        composeRule.onNodeWithTag("habit_preview_icon_exercise").assertExists()
        composeRule.onNodeWithText("保存修改").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }
        assertEquals("exercise", runBlocking { database.habitDao().getHabitById(habitId)?.iconKey })
    }

    @Test
    fun unknownIcon_showsFallbackAndPreservesRawKeyWhenOnlyNameChanges() {
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "旧习惯", description = "", targetPerWeek = 3, createdAt = 100L, iconKey = "cycling")
            )
        }
        val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
        val backClickCount = AtomicInteger(0)
        val stableViewModel = EditHabitViewModel(HabitRepository(database.habitDao(), database.recordDao()), habitId)
        composeRule.setContent {
            EditHabitScreen(stableViewModel, onBackClick = { backClickCount.incrementAndGet() })
        }
        composeRule.onNodeWithTag("habit_preview_icon_general").assertExists()
        composeRule.onNodeWithText("图标 · 原图标暂不可用，预览显示通用").assertExists()
        stableViewModel.onNameChanged("晨跑")
        composeRule.onNodeWithText("保存修改").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }
        assertEquals("cycling", runBlocking { database.habitDao().getHabitById(habitId)?.iconKey })
    }

    @Test
    fun unsavedIconSelection_backDoesNotUpdateRoom() {
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "阅读", description = "", targetPerWeek = 3, createdAt = 100L, iconKey = "reading")
            )
        }
        val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
        val backClickCount = AtomicInteger(0)
        val stableViewModel = EditHabitViewModel(HabitRepository(database.habitDao(), database.recordDao()), habitId)
        composeRule.setContent {
            EditHabitScreen(stableViewModel, onBackClick = { backClickCount.incrementAndGet() })
        }
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backClickCount.get())
        assertEquals("reading", runBlocking { database.habitDao().getHabitById(habitId)?.iconKey })
    }

    @Test
    fun compactViewport_editedNameAndSaveRemainReachable() {
        val longName = "每天晨读并记录书摘和复盘计划"
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "阅读", description = "", targetPerWeek = 3, createdAt = 100L)
            )
        }
        val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
        val backClickCount = AtomicInteger(0)
        val viewModel = EditHabitViewModel(
            repository = HabitRepository(database.habitDao(), database.recordDao()),
            habitId = habitId
        )
        composeRule.setContent {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                EditHabitScreen(
                    viewModel = viewModel,
                    onBackClick = { backClickCount.incrementAndGet() }
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).assertExists()
        viewModel.onNameChanged(longName)
        composeRule.onNodeWithText("保存修改").assertIsNotDisplayed()
        // 编辑页与新增页一致，短视口的保存入口必须能通过滚动触达。
        composeRule.onNodeWithText("保存修改").performScrollTo()
        composeRule.onNodeWithText("保存修改").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }

        assertEquals(longName, runBlocking { database.habitDao().getHabitById(habitId)?.name })
    }

    @Test
    fun edgeToEdgeScreen_withIme_keepsSaveReachable() {
        val longName = "每天晨读并记录书摘和复盘计划"
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "阅读", description = "", targetPerWeek = 3, createdAt = 100L)
            )
        }
        val habitId = runBlocking { database.habitDao().getAllHabits().first().single().id }
        val stableViewModel = EditHabitViewModel(
            repository = HabitRepository(database.habitDao(), database.recordDao()),
            habitId = habitId
        )
        val imeBottomPx = AtomicInteger(0)
        val backClickCount = AtomicInteger(0)
        val keyboardController = AtomicReference<SoftwareKeyboardController>()
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            val controller = LocalSoftwareKeyboardController.current
            SideEffect {
                imeBottomPx.set(imeBottom)
                keyboardController.set(controller)
            }
            EditHabitScreen(
                viewModel = stableViewModel,
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { imeBottomPx.get() > 0 }
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput(longName)
        composeRule.onNodeWithText("保存修改").performScrollTo()

        val saveBounds = composeRule.onNodeWithText("保存修改").getUnclippedBoundsInRoot()
        val density = composeRule.density.density
        val saveBottomPx = saveBounds.bottom.value * density
        val keyboardTopPx = composeRule.activity.window.decorView.height - imeBottomPx.get()
        val titleBottomDp = composeRule.onNodeWithText("编辑习惯")
            .getUnclippedBoundsInRoot().bottom.value
        val minimumKeyboardTopPx =
            (titleBottomDp + (saveBounds.bottom - saveBounds.top).value + 16f) * density
        if (keyboardTopPx >= minimumKeyboardTopPx) {
            assertTrue(
                "保存修改按钮必须位于键盘上方：buttonBottom=$saveBottomPx, keyboardTop=$keyboardTopPx",
                saveBottomPx <= keyboardTopPx + 1f
            )
        } else {
            // 极短横屏无法同时容纳顶部栏和按钮；收起键盘后仍须能滚动并完成编辑。
            composeRule.runOnUiThread { keyboardController.get().hide() }
            composeRule.waitUntil(timeoutMillis = 5_000L) { imeBottomPx.get() == 0 }
            composeRule.onNodeWithText("保存修改").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("保存修改").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }
        assertEquals(longName, runBlocking { database.habitDao().getHabitById(habitId)?.name })
    }
}
