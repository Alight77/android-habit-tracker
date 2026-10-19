package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DashboardContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyMessageStaysCenteredInRemainingSpace() {
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 11)),
                onCheckClick = {},
                onAddClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        val emptyArea = composeRule.onNodeWithTag("dashboardEmptyArea")
            .fetchSemanticsNode().boundsInRoot
        val emptyContent = composeRule.onNodeWithTag("dashboardEmptyContent")
            .fetchSemanticsNode().boundsInRoot
        val headerText = composeRule.onNodeWithText(expectedQuote(LocalDate.of(2026, 10, 11)))
            .fetchSemanticsNode().boundsInRoot

        // 无习惯时提示语随页头与可用高度定位，不能紧贴页头。
        assertTrue(emptyContent.top > headerText.bottom)
        assertTrue(abs(emptyContent.center.y - emptyArea.center.y) < 2f)
        composeRule.onNodeWithText("从一个小习惯开始").assertIsDisplayed()
        composeRule.onNodeWithText("点＋创建习惯").assertIsDisplayed()
    }

    @Test
    fun emptyGuidanceStaysClearOfAddButtonAt320dpAnd2xFont() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, 2f)) {
                Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                    DashboardContent(
                        uiState = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 11)),
                        onCheckClick = {},
                        onAddClick = {},
                        onEditClick = {},
                        onDeleteHabit = {},
                        onRetry = {}
                    )
                }
            }
        }

        val guidance = composeRule.onNodeWithText("点＋创建习惯")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val addButton = composeRule.onNodeWithContentDescription("新增习惯")
            .fetchSemanticsNode().boundsInRoot

        // 放大字号时，空状态引导文字需完整留在悬浮按钮左侧。
        assertTrue(guidance.right < addButton.left)
    }

    @Test
    fun loadingErrorAndEmptyTransitionKeepsFeedbackAndRetry() {
        val state = mutableStateOf<DashboardUiState>(DashboardUiState.Loading)
        val retryCount = AtomicInteger(0)
        composeRule.setContent {
            HabitTrackerTheme {
                DashboardContent(
                    uiState = state.value,
                    onCheckClick = {},
                    onAddClick = {},
                    onEditClick = {},
                    onDeleteHabit = {},
                    onRetry = { retryCount.incrementAndGet() }
                )
            }
        }

        composeRule.onNodeWithText("加载中…").assertIsDisplayed()
        composeRule.runOnUiThread {
            state.value = DashboardUiState.Error(R.string.dashboard_load_failed)
        }
        composeRule.onNodeWithText("加载中…").assertDoesNotExist()
        composeRule.onNodeWithText("加载习惯数据失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertEquals(1, retryCount.get())

        composeRule.runOnUiThread {
            state.value = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 11))
        }
        composeRule.onNodeWithText("加载习惯数据失败，请重试").assertDoesNotExist()
        composeRule.onNodeWithText("点＋创建习惯").assertIsDisplayed()
    }

    @Test
    fun errorRetryRemainsReachableOnShortScreenAt2xFontInDarkTheme() {
        val retryCount = AtomicInteger(0)
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, 2f)) {
                HabitTrackerTheme(darkTheme = true) {
                    Box(modifier = Modifier.width(320.dp).height(240.dp)) {
                        DashboardContent(
                            uiState = DashboardUiState.Error(R.string.dashboard_load_failed),
                            onCheckClick = {},
                            onAddClick = {},
                            onEditClick = {},
                            onDeleteHabit = {},
                            onRetry = { retryCount.incrementAndGet() }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("加载习惯数据失败，请重试").assertExists()
        composeRule.onNodeWithText("重试").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retryCount.get())
    }

    @Test
    fun existingDashboardErrorFollowsUiConfigurationWithoutResubscribing() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).allowMainThreadQueries().build()
        val subscriptions = AtomicInteger(0)
        val failingHabitDao = object : HabitDao by database.habitDao() {
            override fun getAllHabits(): Flow<List<HabitEntity>> = flow {
                subscriptions.incrementAndGet()
                error("habit source unavailable")
            }
        }
        val recordDao = database.recordDao()
        val viewModel = DashboardViewModel(
            habitDao = failingHabitDao,
            recordDao = recordDao,
            repository = HabitRepository(failingHabitDao, recordDao),
            setTodayHabitChecked = SetTodayHabitCheckedUseCase(
                applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
                observeSourceChecked = { flowOf(false) }
            ),
            todaySource = flowOf(LocalDate.of(2026, 10, 11))
        )
        val events = CopyOnWriteArrayList<DashboardUiEvent>()
        val eventCollector = CoroutineScope(Dispatchers.Main.immediate).launch {
            viewModel.events.collect { events += it }
        }
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                DashboardScreen(
                    viewModel = viewModel,
                    onAddClick = {},
                    onEditClick = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            viewModel.uiState.value is DashboardUiState.Error
        }
        val errorState = viewModel.uiState.value as DashboardUiState.Error
        assertEquals(R.string.dashboard_load_failed, errorState.messageResId)
        assertEquals(1, subscriptions.get())

        listOf(
            "zh-CN" to "加载习惯数据失败，请重试",
            "en-US" to "Couldn’t load habit data. Please try again.",
            "en-GB" to "Couldn’t load habit data. Please try again.",
            "ja-JP" to "Couldn’t load habit data. Please try again.",
            "fr-FR" to "Couldn’t load habit data. Please try again."
        ).forEach { (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
            composeRule.onNodeWithText(if (tag == "zh-CN") "重试" else "Retry")
                .assertIsDisplayed()
            assertSame(errorState, viewModel.uiState.value)
            assertEquals(1, subscriptions.get())
            assertTrue("Locale update must not emit a Dashboard event", events.isEmpty())
        }

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { subscriptions.get() == 2 }
        composeRule.onNodeWithText("Couldn’t load habit data. Please try again.")
            .assertIsDisplayed()
        eventCollector.cancel()
        database.close()
    }

    @Test
    fun staleDateSnackbarUsesCurrentUiLanguageWithoutNewBusinessRequest() {
        val displayedDate = LocalDate.of(2026, 10, 11)
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        kotlinx.coroutines.runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(id = 7, name = "Read", description = "", targetPerWeek = 3, createdAt = 0L)
            )
        }
        val useCaseCommands = AtomicInteger(0)
        val refreshRequests = AtomicInteger(0)
        val habitDao = database.habitDao()
        val recordDao = database.recordDao()
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = SetTodayHabitCheckedUseCase(
                applyCommand = {
                    useCaseCommands.incrementAndGet()
                    SetTodayHabitCheckedUseCase.ApplyResult.Accepted
                },
                observeSourceChecked = { flowOf(false) }
            ),
            todaySource = flowOf(displayedDate),
            currentDate = { displayedDate.plusDays(1) },
            requestTodayRefresh = { refreshRequests.incrementAndGet() }
        )
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                DashboardScreen(viewModel = viewModel, onAddClick = {}, onEditClick = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            (viewModel.uiState.value as? DashboardUiState.Success)?.today == displayedDate
        }
        val initialRefreshes = refreshRequests.get()

        val cases = listOf(
            "zh-CN" to "日期已更新，请重新操作",
            "en-US" to "The date has changed. Please try again.",
            "en-GB" to "The date has changed. Please try again.",
            "ja-JP" to "The date has changed. Please try again.",
            "fr-FR" to "The date has changed. Please try again."
        )
        cases.forEachIndexed { index, (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.waitForIdle()
            // 切换界面语言本身不能再产生一次性提示。
            assertTrue(composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty())
            composeRule.runOnUiThread { viewModel.onHabitToggle(7) }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
            assertEquals(initialRefreshes + index + 1, refreshRequests.get())
            assertEquals(0, useCaseCommands.get())
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty()
            }
        }
        database.close()
    }

    @Test
    fun deleteSuccessSnackbarUsesCurrentUiLanguageAt320dpAndDoubleFontScale() {
        val today = LocalDate.now()
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        runBlocking {
            (7..11).forEach { id ->
                database.habitDao().insertHabit(
                    HabitEntity(id = id, name = "Habit $id", description = "", targetPerWeek = 3, createdAt = 0L)
                )
                database.recordDao().setRecordChecked(id, today.toEpochDay(), true)
            }
        }
        val habitDao = database.habitDao()
        val recordDao = database.recordDao()
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = SetTodayHabitCheckedUseCase(
                applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
                observeSourceChecked = { flowOf(false) }
            ),
            todaySource = flowOf(today),
            currentDate = { today }
        )
        val addClickCount = AtomicInteger(0)
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity, 2f)
            ) {
                Box(Modifier.width(320.dp).height(640.dp).testTag("deleteSuccessViewport")) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onAddClick = { addClickCount.incrementAndGet() },
                        onEditClick = {}
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            (viewModel.uiState.value as? DashboardUiState.Success)?.items?.size == 5
        }

        val cases = listOf(
            "zh-CN" to "已从此设备删除该习惯及其打卡记录",
            "en-US" to "Deleted the habit and its check-in history from this device.",
            "en-GB" to "Deleted the habit and its check-in history from this device.",
            "ja-JP" to "Deleted the habit and its check-in history from this device.",
            "fr-FR" to "Deleted the habit and its check-in history from this device."
        )
        cases.forEachIndexed { index, (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.waitForIdle()
            assertTrue(composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty())
            val id = 7 + index
            composeRule.runOnUiThread { viewModel.deleteHabit(id) }
            val snackbarText = composeRule.onNodeWithText(expected, useUnmergedTree = true)
                .assertIsDisplayed()
            if (tag == "en-US") {
                val viewport = composeRule.onNodeWithTag("deleteSuccessViewport")
                    .getUnclippedBoundsInRoot()
                val textBounds = snackbarText.getUnclippedBoundsInRoot()
                assertTrue(textBounds.left >= viewport.left && textBounds.right <= viewport.right)
                assertTrue(textBounds.top >= viewport.top && textBounds.bottom <= viewport.bottom)
                val results = mutableListOf<TextLayoutResult>()
                snackbarText.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertTrue(!layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    assertTrue(layout.getLineLeft(line) >= -1f)
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                }
                val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b2742")
                assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
                val screenshot = composeRule.onNodeWithTag("deleteSuccessViewport")
                    .captureToImage().asAndroidBitmap()
                File(screenshotDir, "en-320-200-delete-success.png").outputStream().use { output ->
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                composeRule.onNodeWithContentDescription("Add habit")
                    .assertIsDisplayed().performClick()
                assertEquals(1, addClickCount.get())
            }
            runBlocking {
                assertEquals(null, habitDao.getHabitById(id))
                assertEquals(null, recordDao.getRecordByDate(id, today.toEpochDay()))
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty()
            }
            composeRule.runOnUiThread { viewModel.deleteHabit(id) }
            composeRule.waitForIdle()
            assertTrue("repeated delete must not show success",
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty())
        }
        database.close()
    }

    @Test
    fun deleteFailureSnackbarUsesCurrentUiLanguageAndKeepsRows() {
        val today = LocalDate.now()
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(id = 7, name = "Read", description = "", targetPerWeek = 3, createdAt = 0L)
            )
            database.recordDao().setRecordChecked(7, today.toEpochDay(), true)
        }
        val failingHabitDao = object : HabitDao by database.habitDao() {
            override suspend fun deleteHabitById(habitId: Int): Int {
                error("controlled delete failure")
            }
        }
        val recordDao = database.recordDao()
        val viewModel = DashboardViewModel(
            habitDao = failingHabitDao,
            recordDao = recordDao,
            repository = HabitRepository(failingHabitDao, recordDao),
            setTodayHabitChecked = SetTodayHabitCheckedUseCase(
                applyCommand = { SetTodayHabitCheckedUseCase.ApplyResult.Accepted },
                observeSourceChecked = { flowOf(false) }
            ),
            todaySource = flowOf(today),
            currentDate = { today }
        )
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration
            ) {
                DashboardScreen(viewModel = viewModel, onAddClick = {}, onEditClick = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            (viewModel.uiState.value as? DashboardUiState.Success)?.items?.singleOrNull()?.id == 7
        }

        val cases = listOf(
            "zh-CN" to "删除习惯失败，请稍后重试",
            "en-US" to "Couldn’t delete habit. Please try again.",
            "en-GB" to "Couldn’t delete habit. Please try again.",
            "ja-JP" to "Couldn’t delete habit. Please try again.",
            "fr-FR" to "Couldn’t delete habit. Please try again."
        )
        cases.forEach { (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.waitForIdle()
            assertTrue(composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty())
            composeRule.runOnUiThread { viewModel.deleteHabit(7) }
            composeRule.onNodeWithText(expected).assertIsDisplayed()
            runBlocking {
                assertTrue(database.habitDao().getHabitById(7) != null)
                assertTrue(recordDao.getRecordByDate(7, today.toEpochDay()) != null)
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty()
            }
        }
        database.close()
    }

    @Test
    fun allCheckInFeedbackResourcesFollowUiLanguageAndEnglishFallback() {
        val messages = listOf(
            Triple(R.string.check_in_persist_failed, "打卡保存失败，请稍后重试", "Couldn’t save check-in. Please try again."),
            Triple(R.string.check_in_convergence_timeout, "打卡状态同步超时，请重试", "Check-in sync timed out. Please try again."),
            Triple(R.string.check_in_cancelled, "打卡操作已中断，请重试", "Check-in was interrupted. Please try again."),
            Triple(R.string.check_in_habit_not_found, "习惯不存在或已删除", "Habit not found or already deleted."),
            Triple(R.string.check_in_conflict, "打卡状态冲突，请重试", "Check-in conflict. Please try again.")
        )
        listOf("zh-CN", "en-US", "en-GB", "ja-JP", "fr-FR").forEach { tag ->
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            val resources = composeRule.activity.createConfigurationContext(configuration).resources
            messages.forEach { (id, chinese, english) ->
                assertEquals("$tag resource $id", if (tag == "zh-CN") chinese else english,
                    resources.getString(id))
            }
        }
    }

    @Test
    fun persistedCheckInFailureShowsLocalizedSnackbarAfterRollback() {
        val today = LocalDate.now()
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java
        ).build()
        runBlocking {
            database.habitDao().insertHabit(
                HabitEntity(id = 7, name = "Read", description = "", targetPerWeek = 3, createdAt = 0L)
            )
        }
        val habitDao = database.habitDao()
        val recordDao = database.recordDao()
        val attempts = AtomicInteger(0)
        val useCase = SetTodayHabitCheckedUseCase(
            applyCommand = {
                attempts.incrementAndGet()
                error("controlled persistence failure")
            },
            observeSourceChecked = { flowOf(false) }
        )
        val viewModel = DashboardViewModel(
            habitDao = habitDao,
            recordDao = recordDao,
            repository = HabitRepository(habitDao, recordDao),
            setTodayHabitChecked = useCase,
            todaySource = flowOf(today),
            currentDate = { today }
        )
        var locale by mutableStateOf(Locale.SIMPLIFIED_CHINESE)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides composeRule.activity.createConfigurationContext(configuration),
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity, 2f)
            ) {
                Box(Modifier.width(320.dp).height(640.dp).testTag("checkInFailureViewport")) {
                    DashboardScreen(viewModel = viewModel, onAddClick = {}, onEditClick = {})
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            (viewModel.uiState.value as? DashboardUiState.Success)?.items?.singleOrNull()?.id == 7
        }
        val cases = listOf(
            "zh-CN" to "打卡保存失败，请稍后重试",
            "en-US" to "Couldn’t save check-in. Please try again.",
            "en-GB" to "Couldn’t save check-in. Please try again.",
            "ja-JP" to "Couldn’t save check-in. Please try again.",
            "fr-FR" to "Couldn’t save check-in. Please try again."
        )
        cases.forEachIndexed { index, (tag, expected) ->
            composeRule.runOnUiThread { locale = Locale.forLanguageTag(tag) }
            composeRule.waitForIdle()
            // 仅重组界面不能重试当前业务意图或重复投递 Snackbar。
            assertEquals(index, attempts.get())
            assertTrue(composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty())
            composeRule.runOnUiThread { viewModel.onHabitToggle(7) }
            val snackbarText = composeRule.onNodeWithText(expected, useUnmergedTree = true)
                .assertIsDisplayed()
            assertEquals(index + 1, attempts.get())
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                (viewModel.uiState.value as? DashboardUiState.Success)
                    ?.items?.singleOrNull()?.isDoneToday == false
            }
            runBlocking { assertEquals(null, recordDao.getRecordByDate(7, today.toEpochDay())) }
            if (tag == "en-US") {
                val viewport = composeRule.onNodeWithTag("checkInFailureViewport")
                    .getUnclippedBoundsInRoot()
                val bounds = snackbarText.getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right)
                assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
                val results = mutableListOf<TextLayoutResult>()
                snackbarText.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertTrue(!layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    assertTrue(layout.getLineLeft(line) >= -1f)
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                }
                val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b2743")
                assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
                val screenshot = composeRule.onNodeWithTag("checkInFailureViewport")
                    .captureToImage().asAndroidBitmap()
                File(screenshotDir, "en-320-200-checkin-persist-failed.png")
                    .outputStream().use { output ->
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText(expected).fetchSemanticsNodes().isEmpty()
            }
        }
        database.close()
    }

    @Test
    fun localizedDashboardErrorFits320dpAtNormalAndDoubleFontScale() {
        val errorState = DashboardUiState.Error(R.string.dashboard_load_failed)
        var locale by mutableStateOf(Locale.US)
        var fontScale by mutableStateOf(1f)
        val retryCount = AtomicInteger(0)
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
                        Box(Modifier.width(320.dp).height(640.dp).testTag("dashboardErrorViewport")) {
                            DashboardContent(
                                uiState = errorState,
                                onCheckClick = {},
                                onAddClick = {},
                                onEditClick = {},
                                onDeleteHabit = {},
                                onRetry = { retryCount.incrementAndGet() }
                            )
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
        val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b2741")
        assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
        cases.forEach { case ->
            composeRule.runOnUiThread {
                locale = case.locale
                fontScale = case.scale
            }
            val errorText = if (case.locale.language == "zh") "加载习惯数据失败，请重试"
                else "Couldn’t load habit data. Please try again."
            val retryText = if (case.locale.language == "zh") "重试" else "Retry"
            val host = composeRule.onNodeWithTag("dashboardErrorViewport").getUnclippedBoundsInRoot()
            val errorNode = composeRule.onNodeWithText(errorText)
                .performScrollTo().assertIsDisplayed()
            val errorBounds = errorNode.getUnclippedBoundsInRoot()
            assertTrue("${case.name}: error text exceeds 320dp horizontally",
                errorBounds.left >= host.left && errorBounds.right <= host.right)
            val results = mutableListOf<TextLayoutResult>()
            errorNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue("${case.name}: error text overflows vertically",
                !results.single().didOverflowHeight)
            for (line in 0 until results.single().lineCount) {
                assertTrue("${case.name}: error line exceeds width",
                    results.single().getLineLeft(line) >= -1f &&
                        results.single().getLineRight(line) <= results.single().size.width + 1f)
            }
            val retryBounds = composeRule.onNodeWithText(retryText)
                .performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("${case.name}: Retry outside 320dp viewport",
                retryBounds.left >= host.left && retryBounds.right <= host.right &&
                    retryBounds.top >= host.top && retryBounds.bottom <= host.bottom)
            val image = composeRule.onNodeWithTag("dashboardErrorViewport").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}-error.png").outputStream().use { output ->
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            composeRule.onNodeWithText(retryText).performClick()
        }
        assertEquals(3, retryCount.get())
    }

    @Test
    fun dateHeaderFollowsSuccessTodayForEmptyDashboard() {
        val firstDate = LocalDate.of(2026, 10, 11)
        val nextDate = firstDate.plusDays(1)
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        }
        val chineseContext = composeRule.activity.createConfigurationContext(configuration)
        val state = mutableStateOf<DashboardUiState>(
            DashboardUiState.Success(emptyList(), firstDate)
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides chineseContext,
                LocalConfiguration provides configuration
            ) {
                DashboardContent(
                    uiState = state.value,
                    onCheckClick = {},
                    onAddClick = {},
                    onEditClick = {},
                    onDeleteHabit = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("10月11日").assertIsDisplayed()
        composeRule.onNodeWithText("周日 · 农历", substring = true).assertIsDisplayed()
        val firstQuote = expectedQuote(firstDate, resources = chineseContext.resources)
        val nextQuote = expectedQuote(nextDate, resources = chineseContext.resources)
        composeRule.onNodeWithText(firstQuote).assertIsDisplayed()

        composeRule.runOnUiThread {
            state.value = DashboardUiState.Success(emptyList(), firstDate)
        }
        composeRule.onNodeWithText(firstQuote).assertIsDisplayed()

        // 模拟既有日期来源更新；页头必须立即跟随 Success.today，而非自行读取系统时钟。
        composeRule.runOnUiThread {
            state.value = DashboardUiState.Success(emptyList(), nextDate)
        }

        composeRule.onNodeWithText("10月12日").assertIsDisplayed()
        composeRule.onNodeWithText("周一 · 农历", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(nextQuote).assertIsDisplayed()
        composeRule.onNodeWithText(firstQuote).assertDoesNotExist()
        composeRule.onNodeWithText("10月11日").assertDoesNotExist()
    }

    @Test
    fun dateHeaderUsesResourceLanguageAndOnlyShowsLunarInChinese() {
        val displayedLocale = mutableStateOf(Locale.forLanguageTag("zh-CN"))
        val displayedDate = mutableStateOf(LocalDate.of(2026, 10, 11))
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(displayedLocale.value)
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration
            ) {
                HabitTrackerTheme { TodayJournalHeader(today = displayedDate.value) }
            }
        }

        val dates = listOf(
            Triple(LocalDate.of(2026, 10, 11), "10月11日" to "周日", "Oct 11" to "Sunday"),
            Triple(LocalDate.of(2026, 12, 31), "12月31日" to "周四", "Dec 31" to "Thursday"),
            Triple(LocalDate.of(2027, 1, 1), "1月1日" to "周五", "Jan 1" to "Friday")
        )
        val locales = listOf(
            Locale.forLanguageTag("zh-CN") to true,
            Locale.forLanguageTag("en-US") to false,
            Locale.forLanguageTag("en-GB") to false,
            Locale.forLanguageTag("ja-JP") to false,
            Locale.forLanguageTag("fr-FR") to false
        )
        for ((locale, chinese) in locales) {
            for ((date, chineseText, englishText) in dates) {
                composeRule.runOnUiThread {
                    displayedLocale.value = locale
                    displayedDate.value = date
                }
                if (chinese) {
                    composeRule.onNodeWithText(chineseText.first).assertIsDisplayed()
                    composeRule.onNodeWithText("${chineseText.second} · 农历", substring = true)
                        .assertIsDisplayed()
                    composeRule.onNodeWithText(englishText.first).assertDoesNotExist()
                } else {
                    composeRule.onNodeWithText(englishText.first).assertIsDisplayed()
                    composeRule.onNodeWithText(englishText.second).assertIsDisplayed()
                    composeRule.onNodeWithText(chineseText.first).assertDoesNotExist()
                    composeRule.onNodeWithText("农历", substring = true).assertDoesNotExist()
                    composeRule.onNodeWithText("周", substring = true).assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun everyDailyQuoteFitsInsideHeaderAt320dpAndDoubleFontScale() {
        val firstDate = LocalDate.of(2026, 10, 11)
        val displayedDate = mutableStateOf(firstDate)
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        }
        val chineseContext = composeRule.activity.createConfigurationContext(configuration)
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides chineseContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity.density, 2f)
            ) {
                HabitTrackerTheme {
                    Box(Modifier.width(320.dp).testTag("narrowQuoteArea")) {
                        TodayJournalHeader(today = displayedDate.value)
                    }
                }
            }
        }

        val quoteCount = chineseContext.resources
            .getStringArray(R.array.dashboard_daily_quotes).size
        repeat(quoteCount) { dayOffset ->
            val date = firstDate.plusDays(dayOffset.toLong())
            composeRule.runOnUiThread { displayedDate.value = date }
            val quoteBounds = composeRule.onNodeWithText(
                expectedQuote(date, narrowLargeText = true, resources = chineseContext.resources)
            )
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val area = composeRule.onNodeWithTag("narrowQuoteArea")
                .fetchSemanticsNode().boundsInRoot

            assertTrue("quote exceeds 320dp width", quoteBounds.left >= area.left - 1f)
            assertTrue("quote exceeds 320dp width", quoteBounds.right <= area.right + 1f)
            assertTrue("quote exceeds header height", quoteBounds.top >= area.top - 1f)
            assertTrue("quote exceeds header height", quoteBounds.bottom <= area.bottom + 1f)
        }
    }

    @Test
    fun englishQuotesKeepResourceWordBoundariesAt320dpAndDoubleFontScale() {
        val firstDate = LocalDate.of(2026, 10, 11)
        val displayedDate = mutableStateOf(firstDate)
        val displayedLocale = mutableStateOf(Locale.US)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(displayedLocale.value)
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity.density, 2f)
            ) {
                HabitTrackerTheme {
                    Box(Modifier.width(320.dp)) {
                        TodayJournalHeader(today = displayedDate.value)
                    }
                }
            }
        }

        for (locale in listOf("en-US", "en-GB", "ja-JP", "fr-FR")) {
            val resources = composeRule.activity.createConfigurationContext(
                Configuration(composeRule.activity.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(locale))
                }
            ).resources
            val quotes = resources.getStringArray(R.array.dashboard_daily_quotes)
            assertEquals(18, quotes.size)
            composeRule.runOnUiThread { displayedLocale.value = Locale.forLanguageTag(locale) }
            repeat(quotes.size) { offset ->
                val date = firstDate.plusDays(offset.toLong())
                composeRule.runOnUiThread { displayedDate.value = date }
                // 资源原有换行可保留；页面不能在普通英语单词中再插入换行。
                composeRule.onNodeWithText(quotes[dailyQuoteIndex(date, quotes.size)])
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun allDailyQuotesStayInsideTheirContainerWithoutClippingOrEnglishWordSplits() {
        data class Scenario(val languageTag: String, val widthDp: Int, val fontScale: Float, val dark: Boolean)

        val firstDate = LocalDate.of(2026, 10, 11)
        val displayedDate = mutableStateOf(firstDate)
        val scenario = mutableStateOf(Scenario("en-US", 360, 1f, false))
        composeRule.setContent {
            val settings = scenario.value
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(settings.languageTag))
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity.density, settings.fontScale)
            ) {
                HabitTrackerTheme(darkTheme = settings.dark) {
                    Box(Modifier.width(settings.widthDp.dp)) {
                        TodayJournalHeader(today = displayedDate.value)
                    }
                }
            }
        }

        val scenarios = listOf(
            Scenario("en-US", 360, 1f, false),
            Scenario("en-US", 320, 1f, false),
            Scenario("en-US", 320, 2f, false),
            Scenario("en-US", 320, 2f, true),
            Scenario("en-GB", 320, 2f, false),
            Scenario("ja-JP", 320, 2f, false),
            Scenario("fr-FR", 320, 2f, false),
            Scenario("zh-CN", 320, 2f, false)
        )
        for (settings in scenarios) {
            val resources = composeRule.activity.createConfigurationContext(
                Configuration(composeRule.activity.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(settings.languageTag))
                }
            ).resources
            val quotes = resources.getStringArray(R.array.dashboard_daily_quotes)
            assertEquals(18, quotes.size)
            composeRule.runOnUiThread { scenario.value = settings }
            repeat(quotes.size) { offset ->
                val date = firstDate.plusDays(offset.toLong())
                composeRule.runOnUiThread { displayedDate.value = date }
                val resourceQuote = quotes[dailyQuoteIndex(date, quotes.size)]
                val displayedQuote = if (settings.languageTag == "zh-CN") {
                    balanceQuoteForNarrowLargeText(resourceQuote)
                } else {
                    resourceQuote
                }
                val textNode = composeRule.onNodeWithText(displayedQuote, useUnmergedTree = true)
                    .assertIsDisplayed()
                val results = mutableListOf<TextLayoutResult>()
                textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertTrue(
                    "${settings.languageTag} quote ${offset + 1} vertical overflow",
                    !layout.didOverflowHeight
                )
                assertEquals(displayedQuote, layout.layoutInput.text.text)
                val textBounds = textNode.fetchSemanticsNode().boundsInRoot
                val containerBounds = composeRule.onNodeWithTag("dailyQuoteContainer", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                for (line in 0 until layout.lineCount) {
                    assertTrue(
                        "${settings.languageTag} quote ${offset + 1} line ${line + 1} exceeds width " +
                            "left=${layout.getLineLeft(line)} right=${layout.getLineRight(line)} " +
                            "layoutWidth=${layout.size.width}",
                        layout.getLineLeft(line) >= -1f &&
                            layout.getLineRight(line) <= layout.size.width + 1f
                    )
                }
                assertTrue("quote ${offset + 1} exceeds paper left", textBounds.left >= containerBounds.left - 1f)
                assertTrue("quote ${offset + 1} exceeds paper right", textBounds.right <= containerBounds.right + 1f)
                assertTrue("quote ${offset + 1} exceeds paper top", textBounds.top >= containerBounds.top - 1f)
                assertTrue("quote ${offset + 1} exceeds paper bottom", textBounds.bottom <= containerBounds.bottom + 1f)
                if (settings.languageTag != "zh-CN") {
                    for (line in 0 until layout.lineCount - 1) {
                        val end = layout.getLineEnd(line)
                        val text = layout.layoutInput.text.text
                        val splitsWord = end in 1 until text.length &&
                            text[end - 1].isLetter() && text[end].isLetter()
                        assertTrue("${settings.languageTag} quote ${offset + 1} splits an English word", !splitsWord)
                    }
                }
            }
        }
    }

    @Test
    fun longestEnglishQuoteKeepsHabitCardReachableAt320dpAndDoubleFontScale() {
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(Locale.US)
        }
        val englishContext = composeRule.activity.createConfigurationContext(configuration)
        val quotes = englishContext.resources.getStringArray(R.array.dashboard_daily_quotes)
        val firstDate = LocalDate.of(2026, 10, 11)
        val quoteDate = (0 until quotes.size)
            .map { firstDate.plusDays(it.toLong()) }
            .first { dailyQuoteIndex(it, quotes.size) == quotes.lastIndex }
        val habitName = "Read every day"

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides englishContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity.density, 2f)
            ) {
                HabitTrackerTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        DashboardContent(
                            uiState = DashboardUiState.Success(
                                items = listOf(
                                    HabitItemUiState(
                                        id = 1,
                                        name = habitName,
                                        targetPerWeek = 3,
                                        isDoneToday = false,
                                        streak = 0,
                                        recentDays = emptyList()
                                    )
                                ),
                                today = quoteDate
                            ),
                            onCheckClick = {},
                            onAddClick = {},
                            onEditClick = {},
                            onDeleteHabit = {},
                            onRetry = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(quotes.last()).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(1)
        composeRule.onNodeWithText(habitName).assertIsDisplayed()
    }

    @Test
    fun dateHeaderAndCardsShareScrollableSuccessContent() {
        val items = (1..12).map { index ->
            HabitItemUiState(
                id = index,
                name = "习惯 $index",
                targetPerWeek = 3,
                isDoneToday = false,
                streak = 0,
                recentDays = emptyList()
            )
        }
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Success(items, LocalDate.of(2026, 10, 11)),
                onCheckClick = {},
                onAddClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("10月11日").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        composeRule.onNodeWithText("习惯 12").assertIsDisplayed()
    }

    @Test
    fun moreMenu_navigatesToEditAndConfirmsDeletion() {
        val editedHabitId = AtomicInteger(0)
        val deletedHabitId = AtomicInteger(0)
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Success(
                    listOf(
                        HabitItemUiState(
                            id = 7,
                            name = "阅读",
                            targetPerWeek = 3,
                            isDoneToday = false,
                            streak = 0,
                            recentDays = emptyList()
                        )
                    ),
                    today = LocalDate.of(2026, 9, 19)
                ),
                onCheckClick = { _ -> },
                onAddClick = {},
                onEditClick = { editedHabitId.set(it) },
                onDeleteHabit = { deletedHabitId.set(it) },
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("编辑").performClick()
        assertEquals(7, editedHabitId.get())

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("删除“阅读”及其全部打卡记录？应用内无法撤销；如有系统备份，恢复后可能重现。")
            .assertIsDisplayed()
        assertEquals(0, deletedHabitId.get())

        composeRule.onNodeWithText("确认删除").performClick()
        assertEquals(7, deletedHabitId.get())
    }

    @Test
    fun addAction_exposesContentDescriptionAndInvokesCallback() {
        val addClickCount = AtomicInteger(0)
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 9, 19)),
                onCheckClick = { _ -> },
                onAddClick = { addClickCount.incrementAndGet() },
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("新增习惯").performClick()

        assertEquals(1, addClickCount.get())
    }

    @Test
    fun deleteDialogExplainsLocalLossAndBackupRestoreAndCancelKeepsCallbackUntouched() {
        val deletedHabitId = AtomicInteger(0)
        composeRule.setContent {
            DashboardContent(
                uiState = singleHabitState(),
                onCheckClick = {},
                onAddClick = {},
                onEditClick = {},
                onDeleteHabit = { deletedHabitId.set(it) },
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        // 本机删除不可在应用内撤销，但旧系统备份可能带回记录；两层后果都应可朗读。
        composeRule.onNodeWithText("删除“阅读”及其全部打卡记录？应用内无法撤销；如有系统备份，恢复后可能重现。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("确认删除").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        assertEquals(0, deletedHabitId.get())
    }

    @Test
    fun rapidDoubleConfirmDispatchesOnlySelectedHabitOnce() {
        val deletedHabitIds = mutableListOf<Int>()
        composeRule.setContent {
            DashboardContent(
                uiState = singleHabitState(),
                onCheckClick = {},
                onAddClick = {},
                onEditClick = {},
                onDeleteHabit = { deletedHabitIds += it },
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        // 同一坐标快速双击；第一击关闭弹窗后，第二击不得删除其他习惯或重复派发。
        composeRule.onNodeWithText("确认删除").performTouchInput { doubleClick() }

        assertEquals(listOf(7), deletedHabitIds)
    }

    private fun singleHabitState() = DashboardUiState.Success(
        items = listOf(
            HabitItemUiState(
                id = 7,
                name = "阅读",
                targetPerWeek = 3,
                isDoneToday = true,
                streak = 1,
                recentDays = emptyList()
            )
        ),
        today = LocalDate.of(2026, 9, 19)
    )

    private fun expectedQuote(
        date: LocalDate,
        narrowLargeText: Boolean = false,
        resources: Resources = composeRule.activity.resources
    ): String {
        val quotes = resources.getStringArray(R.array.dashboard_daily_quotes)
        val quote = quotes[dailyQuoteIndex(date, quotes.size)]
        return if (narrowLargeText) balanceQuoteForNarrowLargeText(quote) else quote
    }
}
