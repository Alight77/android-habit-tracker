package io.github.alight77.habittracker.feature.edit

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
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.alight77.habittracker.domain.icon.IconKey
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

    @Test
    fun existingNameErrorFollowsUiConfigurationAndKeepsDraft() {
        val habitId = insertTestHabit()
        val stableViewModel = EditHabitViewModel(
            HabitRepository(database.habitDao(), database.recordDao()), habitId
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) { !stableViewModel.uiState.value.isLoading }
        stableViewModel.onNameChanged("   ")
        stableViewModel.selectIcon(IconKey.WATER)
        stableViewModel.decreaseTargetPerWeek()
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
                EditHabitScreen(stableViewModel, onBackClick = {})
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
            assertEquals("   ", stableViewModel.uiState.value.name)
            assertEquals(IconKey.WATER, stableViewModel.uiState.value.iconKey)
            assertEquals(2, stableViewModel.uiState.value.targetPerWeek)
        }
        assertEquals("阅读", runBlocking { database.habitDao().getHabitById(habitId)?.name })
    }

    @Test
    fun existingNotFoundErrorFollowsUiConfigurationWithoutReload() {
        assertLoadErrorFollowsUiConfiguration(
            failOnLoad = false,
            expectedResId = R.string.edit_habit_not_found,
            chinese = "未找到该习惯",
            english = "Habit not found."
        )
    }

    @Test
    fun existingLoadFailureFollowsUiConfigurationWithoutReload() {
        assertLoadErrorFollowsUiConfiguration(
            failOnLoad = true,
            expectedResId = R.string.edit_habit_load_failed,
            chinese = "加载习惯失败，请稍后重试",
            english = "Couldn’t load habit. Please try again."
        )
    }

    @Test
    fun saveFailureSnackbarUsesLocaleWhenEachEventIsConsumed() {
        val habitId = insertTestHabit()
        val failingHabitDao = object : HabitDao by database.habitDao() {
            override suspend fun updateHabit(habit: HabitEntity) {
                error("database unavailable")
            }
        }
        val stableViewModel = EditHabitViewModel(
            HabitRepository(failingHabitDao, database.recordDao()), habitId
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) { !stableViewModel.uiState.value.isLoading }
        stableViewModel.onNameChanged("Draft")
        stableViewModel.selectIcon(IconKey.WATER)
        stableViewModel.decreaseTargetPerWeek()
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                EditHabitScreen(stableViewModel, onBackClick = {})
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
            val saveLabel = if (tag == "zh-CN") "保存修改" else "Save changes"
            composeRule.onNodeWithText(saveLabel).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
            assertEquals("Draft", stableViewModel.uiState.value.name)
            assertEquals(IconKey.WATER, stableViewModel.uiState.value.iconKey)
            assertEquals(2, stableViewModel.uiState.value.targetPerWeek)
            assertEquals("阅读", runBlocking { database.habitDao().getHabitById(habitId)?.name })
            if (index < 4) {
                composeRule.waitUntil(timeoutMillis = 10_000L) {
                    composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty()
                }
            }
        }
    }

    @Test
    fun localizedValidationAndSaveFit320dpAtNormalAndDoubleFontScale() {
        val habitId = insertTestHabit()
        val stableViewModel = EditHabitViewModel(
            HabitRepository(database.habitDao(), database.recordDao()), habitId
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) { !stableViewModel.uiState.value.isLoading }
        stableViewModel.onNameChanged("   ")
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
                        Box(Modifier.width(320.dp).height(700.dp).testTag("narrowEditHabit")) {
                            EditHabitScreen(stableViewModel, onBackClick = {})
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
        val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b273")
        assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
        cases.forEach { case ->
            composeRule.runOnUiThread {
                locale = case.locale
                fontScale = case.scale
            }
            val errorLabel = if (case.locale.language == "zh") "请输入习惯名称"
                else "Please enter a habit name."
            val saveLabel = if (case.locale.language == "zh") "保存修改" else "Save changes"
            val host = composeRule.onNodeWithTag("narrowEditHabit").getUnclippedBoundsInRoot()
            val error = composeRule.onNodeWithText(errorLabel)
                .performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("${case.name}: error clipped horizontally",
                error.left >= host.left && error.right <= host.right)
            val errorImage = composeRule.onNodeWithTag("narrowEditHabit").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}-error.png").outputStream().use { output ->
                assertTrue(errorImage.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            composeRule.onNodeWithTag("habit_name_input").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(saveLabel).performScrollTo().assertIsDisplayed()
            if (case.scale >= 2f) {
                composeRule.onNodeWithTag("narrowEditHabit").performTouchInput {
                    swipeUp(startY = bottom * 0.8f, endY = bottom * 0.35f)
                }
            }
            val save = composeRule.onNodeWithText(saveLabel)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("${case.name}: save action outside 320dp viewport",
                save.left >= host.left && save.right <= host.right &&
                    save.top >= host.top && save.bottom <= host.bottom)
            val saveImage = composeRule.onNodeWithTag("narrowEditHabit").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}-save.png").outputStream().use { output ->
                assertTrue(saveImage.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun localizedLoadFailureRetryFits320dpAtDoubleFontScale() {
        val readCount = AtomicInteger(0)
        val failingDao = object : HabitDao by database.habitDao() {
            override suspend fun getHabitById(habitId: Int): HabitEntity? {
                readCount.incrementAndGet()
                error("database unavailable")
            }
        }
        val stableViewModel = EditHabitViewModel(
            HabitRepository(failingDao, database.recordDao()), 99
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) { !stableViewModel.uiState.value.isLoading }
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
                        Box(Modifier.width(320.dp).height(700.dp).testTag("narrowEditError")) {
                            EditHabitScreen(stableViewModel, onBackClick = {})
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
        val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b273")
        assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
        cases.forEach { case ->
            composeRule.runOnUiThread {
                locale = case.locale
                fontScale = case.scale
            }
            val errorLabel = if (case.locale.language == "zh") "加载习惯失败，请稍后重试"
                else "Couldn’t load habit. Please try again."
            val retryLabel = if (case.locale.language == "zh") "重试" else "Retry"
            val host = composeRule.onNodeWithTag("narrowEditError").getUnclippedBoundsInRoot()
            val error = composeRule.onNodeWithText(errorLabel).assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            val retry = composeRule.onNodeWithText(retryLabel).performScrollTo().assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue("${case.name}: load error clipped horizontally",
                error.left >= host.left && error.right <= host.right)
            assertTrue("${case.name}: retry outside viewport",
                retry.left >= host.left && retry.right <= host.right &&
                    retry.top >= host.top && retry.bottom <= host.bottom)
            val image = composeRule.onNodeWithTag("narrowEditError").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}-load-error.png").outputStream().use { output ->
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { readCount.get() == 2 }
        composeRule.onNodeWithText("加载习惯失败，请稍后重试").assertIsDisplayed()
    }

    private fun assertLoadErrorFollowsUiConfiguration(
        failOnLoad: Boolean,
        expectedResId: Int,
        chinese: String,
        english: String
    ) {
        val readCount = AtomicInteger(0)
        val habitDao = object : HabitDao by database.habitDao() {
            override suspend fun getHabitById(habitId: Int): HabitEntity? {
                readCount.incrementAndGet()
                if (failOnLoad) error("database unavailable")
                return null
            }
        }
        val stableViewModel = EditHabitViewModel(
            HabitRepository(habitDao, database.recordDao()), 99
        )
        composeRule.waitUntil(timeoutMillis = 5_000L) { !stableViewModel.uiState.value.isLoading }
        val errorState = stableViewModel.uiState.value
        assertEquals(expectedResId, errorState.loadErrorResId)
        assertEquals(1, readCount.get())
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                EditHabitScreen(stableViewModel, onBackClick = {})
            }
        }

        listOf(
            "zh-CN" to chinese,
            "en-US" to english,
            "en-GB" to english,
            "ja-JP" to english,
            "fr-FR" to english
        ).forEach { (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
            composeRule.onNodeWithText(if (tag == "zh-CN") "重试" else "Retry")
                .assertIsDisplayed()
            assertSame(errorState, stableViewModel.uiState.value)
            assertEquals(1, readCount.get())
        }
    }

    private fun insertTestHabit(): Int {
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(name = "阅读", description = "", targetPerWeek = 3, createdAt = 100L)
            )
        }
        return runBlocking { database.habitDao().getAllHabits().first().single().id }
    }
}
