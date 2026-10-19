package io.github.alight77.habittracker.feature.addhabit

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.Locale
import java.io.File

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
    fun addPage_showsCreationGuidance() {
        composeRule.setContent {
            AddHabitScreen(viewModel = viewModel(), onBackClick = {})
        }

        composeRule.onNodeWithText("种下一个新习惯").assertIsDisplayed()
        composeRule.onNodeWithText("从今天开始，遇见更好的自己。").assertIsDisplayed()
        composeRule.onNodeWithText("习惯预览").assertIsDisplayed()
        composeRule.onNodeWithText("试试这些名称").assertExists()
        composeRule.onNodeWithText("每周计划 7 次，目标是每天一次。").assertExists()
        composeRule.onNodeWithText("保存习惯").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nameSuggestion_updatesInputAndLivePreview() {
        val stableViewModel = viewModel()
        composeRule.setContent {
            AddHabitScreen(viewModel = stableViewModel, onBackClick = {})
        }

        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals("待命名习惯")
        composeRule.onNodeWithTag("habit_name_suggestion_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_name_input").assertTextContains("阅读")
        composeRule.onNodeWithTag("habit_preview_name").assertTextEquals("阅读")
        composeRule.onNodeWithTag("habit_preview_target").assertTextEquals("每周目标 7 次")
        composeRule.onNodeWithTag("habit_preview_icon_reading").assertExists()
    }

    @Test
    fun automaticManualAndRestore_persistFinalIconInRoom() {
        val backClickCount = AtomicInteger(0)
        val stableViewModel = viewModel()
        composeRule.setContent {
            AddHabitScreen(stableViewModel, onBackClick = { backClickCount.incrementAndGet() })
        }
        composeRule.onNodeWithText("图标 · 自动推荐：通用").assertExists()
        composeRule.onNodeWithTag("habit_name_suggestion_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_icon_reading").assertExists()
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_reading").assertIsSelected()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()
        stableViewModel.onNameChanged("晨跑")
        composeRule.onNodeWithTag("habit_preview_icon_water").assertExists()
        composeRule.onNodeWithTag("habit_icon_action").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_preview_icon_exercise").assertExists()
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithTag("habit_icon_option_water").performScrollTo().performClick()
        composeRule.onNodeWithText("保存习惯").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }
        assertEquals("water", runBlocking { database.habitDao().getAllHabits().first().single().iconKey })
    }

    @Test
    fun pickerDismiss_keepsAutomaticModeAndDoesNotWrite() {
        composeRule.setContent { AddHabitScreen(viewModel(), onBackClick = {}) }
        composeRule.onNodeWithTag("habit_choose_icon").performScrollTo().performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("图标 · 自动推荐：通用").assertExists()
        assertTrue(runBlocking { database.habitDao().getAllHabits().first().isEmpty() })
    }

    @Test
    fun saveHabit_withBlankName_showsValidationError() {
        composeRule.setContent {
            AddHabitScreen(
                viewModel = viewModel(),
                onBackClick = {}
            )
        }

        composeRule.onNodeWithText("保存习惯").performScrollTo().performClick()

        composeRule.onNodeWithText("请输入习惯名称").assertIsDisplayed()
    }

    @Test
    fun existingNameErrorFollowsUiConfigurationWithoutRevalidating() {
        val stableViewModel = viewModel()
        stableViewModel.saveHabit()
        val errorState = stableViewModel.uiState.value
        assertEquals(R.string.habit_name_required, errorState.nameErrorResId)
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                AddHabitScreen(stableViewModel, onBackClick = {})
            }
        }

        listOf(
            "zh-CN" to "请输入习惯名称",
            "en-US" to "Please enter a habit name.",
            "en-GB" to "Please enter a habit name.",
            "ja-JP" to "Please enter a habit name.",
            "fr-FR" to "Please enter a habit name."
        ).forEach { (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
            assertSame(errorState, stableViewModel.uiState.value)
        }
        assertTrue(runBlocking { database.habitDao().getAllHabits().first().isEmpty() })
    }

    @Test
    fun saveHabit_withValidName_persistsHabitAndNavigatesBack() {
        val backClickCount = AtomicInteger(0)
        val stableViewModel = viewModel()
        composeRule.setContent {
            AddHabitScreen(
                viewModel = stableViewModel,
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("阅读")
        composeRule.onNodeWithText("保存习惯").performScrollTo().performClick()
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
        // IME Insets 变化会触发重组；测试与实际 Activity 一样持有同一个 ViewModel。
        val stableViewModel = viewModel()
        composeRule.setContent {
            AddHabitScreen(
                viewModel = stableViewModel,
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNodeWithText("＋").assertIsNotEnabled()
        repeat(2) {
            composeRule.onNodeWithContentDescription("减少每周目标次数")
                .performScrollTo()
                .performClick()
        }
        composeRule.onNodeWithText("5 次 / 周").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("每周计划 5 次，可以灵活安排时间。").assertExists()
        composeRule.onNodeWithTag("habit_preview_target").assertTextEquals("每周目标 5 次")

        composeRule.onNode(hasSetTextAction()).performTextInput("阅读")
        composeRule.onNodeWithText("保存习惯").performScrollTo().performClick()
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
        assertTrue(runBlocking { database.habitDao().getAllHabits().first().isEmpty() })
    }

    @Test
    fun saveHabit_whenWriteFails_showsFeedbackAndKeepsInput() {
        val imeBottomPx = AtomicInteger(0)
        val keyboardController = AtomicReference<SoftwareKeyboardController>()
        val failingHabitDao = object : HabitDao by database.habitDao() {
            override suspend fun insertHabit(habit: HabitEntity) {
                error("database unavailable")
            }
        }
        val stableViewModel = AddHabitViewModel(
            HabitRepository(failingHabitDao, database.recordDao())
        )
        composeRule.setContent {
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            val controller = LocalSoftwareKeyboardController.current
            SideEffect {
                imeBottomPx.set(imeBottom)
                keyboardController.set(controller)
            }
            AddHabitScreen(viewModel = stableViewModel, onBackClick = {})
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("阅读")
        composeRule.onNodeWithText("保存习惯").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("保存失败，请稍后重试")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("保存失败，请稍后重试").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextContains("阅读")
        assertTrue(runBlocking { database.habitDao().getAllHabits().first().isEmpty() })
        // 失败态测试结束前收起键盘，避免输入法状态遗留到下一个 Activity 用例。
        composeRule.runOnUiThread { keyboardController.get().hide() }
        composeRule.waitUntil(timeoutMillis = 5_000L) { imeBottomPx.get() == 0 }
    }

    @Test
    fun saveFailureSnackbarUsesLocaleWhenEachEventIsConsumed() {
        val failingHabitDao = object : HabitDao by database.habitDao() {
            override suspend fun insertHabit(habit: HabitEntity) {
                error("database unavailable")
            }
        }
        val stableViewModel = AddHabitViewModel(
            HabitRepository(failingHabitDao, database.recordDao())
        )
        stableViewModel.onNameChanged("Read")
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                AddHabitScreen(stableViewModel, onBackClick = {})
            }
        }

        listOf(
            "zh-CN" to "保存失败，请稍后重试",
            "en-US" to "Couldn’t save habit. Please try again.",
            "en-GB" to "Couldn’t save habit. Please try again.",
            "ja-JP" to "Couldn’t save habit. Please try again.",
            "fr-FR" to "Couldn’t save habit. Please try again."
        ).forEachIndexed { index, (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            val saveLabel = if (tag == "zh-CN") "保存习惯" else "Save habit"
            composeRule.onNodeWithText(saveLabel).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
            composeRule.onNodeWithText(saveLabel).performScrollTo().assertIsDisplayed()
            assertEquals("Read", stableViewModel.uiState.value.name)
            assertTrue(runBlocking { database.habitDao().getAllHabits().first().isEmpty() })
            if (index < 4) {
                composeRule.waitUntil(timeoutMillis = 10_000L) {
                    composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty()
                }
            }
        }
    }

    @Test
    fun localizedValidationAndSaveFit320dpAtNormalAndDoubleFontScale() {
        val stableViewModel = viewModel()
        stableViewModel.saveHabit()
        var locale by mutableStateOf(Locale.US)
        var fontScale by mutableStateOf(1f)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity, fontScale)
            ) {
                key(locale.toLanguageTag(), fontScale) {
                    HabitTrackerTheme {
                        Box(Modifier.width(320.dp).height(700.dp).testTag("narrowAddHabit")) {
                            AddHabitScreen(stableViewModel, onBackClick = {})
                        }
                    }
                }
            }
        }

        data class VisualCase(val name: String, val locale: Locale, val scale: Float)
        val cases = listOf(
            VisualCase("en-320-100", Locale.US, 1f),
            VisualCase("en-320-200", Locale.US, 2f),
            VisualCase("zh-320-200", Locale.SIMPLIFIED_CHINESE, 2f)
        )
        val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b272")
        assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
        cases.forEach { case ->
            composeRule.runOnUiThread {
                locale = case.locale
                fontScale = case.scale
            }
            val errorLabel = if (case.locale.language == "zh") "请输入习惯名称"
                else "Please enter a habit name."
            val saveLabel = if (case.locale.language == "zh") "保存习惯" else "Save habit"
            val host = composeRule.onNodeWithTag("narrowAddHabit").getUnclippedBoundsInRoot()
            val error = composeRule.onNodeWithText(errorLabel)
                .performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("${case.name}: error clipped horizontally",
                error.left >= host.left && error.right <= host.right)
            val topImage = composeRule.onNodeWithTag("narrowAddHabit").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}-error.png").outputStream().use { output ->
                assertTrue(topImage.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            composeRule.onNodeWithTag("habit_name_input").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(saveLabel).performScrollTo().assertIsDisplayed()
            if (case.scale >= 2f) {
                // 大字号下继续向上滚动，核对完整按钮而非仅露出文字上沿。
                composeRule.onNodeWithTag("narrowAddHabit").performTouchInput {
                    swipeUp(startY = bottom * 0.8f, endY = bottom * 0.35f)
                }
            }
            val save = composeRule.onNodeWithText(saveLabel)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("${case.name}: save action outside 320dp viewport",
                save.left >= host.left && save.right <= host.right &&
                    save.top >= host.top && save.bottom <= host.bottom)
            val saveImage = composeRule.onNodeWithTag("narrowAddHabit").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}-save.png").outputStream().use { output ->
                assertTrue(saveImage.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun compactViewport_longNameAndSaveRemainReachable() {
        val longName = "每天阅读三十分钟并记录心得和复盘计划"
        val backClickCount = AtomicInteger(0)
        val stableViewModel = viewModel()
        stableViewModel.onNameChanged(longName)
        composeRule.setContent {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                AddHabitScreen(
                    viewModel = stableViewModel,
                    onBackClick = { backClickCount.incrementAndGet() }
                )
            }
        }

        composeRule.onNodeWithText("保存习惯").assertIsNotDisplayed()
        // 短视口与键盘遮挡分别验证，避免让人工容器承受大于自身高度的系统 IME Insets。
        composeRule.onNodeWithText("保存习惯").performScrollTo()
        composeRule.onNodeWithText("保存习惯").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }

        assertEquals(longName, runBlocking { database.habitDao().getAllHabits().first().single().name })
    }

    @Test
    fun edgeToEdgeScreen_withIme_keepsSaveReachable() {
        val longName = "每天阅读三十分钟并记录心得和复盘计划"
        val imeBottomPx = AtomicInteger(0)
        val backClickCount = AtomicInteger(0)
        val keyboardController = AtomicReference<SoftwareKeyboardController>()
        val stableViewModel = viewModel()
        composeRule.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            val controller = LocalSoftwareKeyboardController.current
            SideEffect {
                imeBottomPx.set(imeBottom)
                keyboardController.set(controller)
            }
            AddHabitScreen(
                viewModel = stableViewModel,
                onBackClick = { backClickCount.incrementAndGet() }
            )
        }

        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { imeBottomPx.get() > 0 }
        composeRule.onNode(hasSetTextAction()).performTextInput(longName)
        composeRule.onNodeWithText("保存习惯").performScrollTo()

        val saveBounds = composeRule.onNodeWithText("保存习惯").getUnclippedBoundsInRoot()
        val density = composeRule.density.density
        val saveBottomPx = saveBounds.bottom.value * density
        val keyboardTopPx = composeRule.activity.window.decorView.height - imeBottomPx.get()
        val titleBottomDp = composeRule.onNodeWithText("新增习惯")
            .getUnclippedBoundsInRoot().bottom.value
        val minimumKeyboardTopPx =
            (titleBottomDp + (saveBounds.bottom - saveBounds.top).value + 16f) * density
        if (keyboardTopPx >= minimumKeyboardTopPx) {
            assertTrue(
                "保存按钮必须位于键盘上方：buttonBottom=$saveBottomPx, keyboardTop=$keyboardTopPx",
                saveBottomPx <= keyboardTopPx + 1f
            )
        } else {
            // 极短横屏无法同时容纳顶部栏和按钮；收起键盘后仍须能滚动并完成保存。
            composeRule.runOnUiThread { keyboardController.get().hide() }
            composeRule.waitUntil(timeoutMillis = 5_000L) { imeBottomPx.get() == 0 }
            composeRule.onNodeWithText("保存习惯").performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("保存习惯").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { backClickCount.get() == 1 }
        assertEquals(longName, runBlocking { database.habitDao().getAllHabits().first().single().name })
    }

    private fun viewModel(): AddHabitViewModel {
        return AddHabitViewModel(
            HabitRepository(database.habitDao(), database.recordDao())
        )
    }
}
