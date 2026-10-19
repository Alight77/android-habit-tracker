package io.github.alight77.habittracker.feature.stats

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import io.github.alight77.habittracker.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitDatabase
import io.github.alight77.habittracker.data.local.HabitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class StatsContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun privacyFooterIsAvailableAcrossStatsStatesAboveBottomBar() {
        var state by mutableStateOf<StatsUiState>(StatsUiState.Loading)
        composeRule.setContent {
            StatsContent(
                uiState = state,
                onRetry = {},
                bottomBar = {
                    Box(Modifier.fillMaxWidth().height(56.dp).testTag("testBottomBar"))
                },
                onOpenPrivacyPolicy = { true }
            )
        }

        listOf(
            StatsUiState.Loading,
            StatsUiState.Success(StatsSummary(0, 0, 0, 0, 0, emptyList())),
            StatsUiState.Success(StatsSummary(1, 0, 0, 0, 0, emptyList())),
            StatsUiState.Error(R.string.stats_load_failed)
        ).forEach { nextState ->
            composeRule.runOnUiThread { state = nextState }
            val footer = composeRule.onNodeWithText("隐私政策")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
            val bottomBarTop = composeRule.onNodeWithTag("testBottomBar")
                .fetchSemanticsNode().boundsInRoot.top
            val minTapPixels = 48f * composeRule.activity.resources.displayMetrics.density
            assertTrue("privacy footer touch target is too small", footer.width >= minTapPixels - 1f)
            assertTrue("privacy footer touch target is too short", footer.height >= minTapPixels - 1f)
            assertTrue("privacy footer overlaps bottom navigation", footer.bottom <= bottomBarTop + 1f)
        }
    }

    @Test
    fun privacyFooterOpensExpectedHttpsUrlWithoutChangingStats() {
        var openedUrl: String? = null
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Success(StatsSummary(1, 0, 0, 0, 0, emptyList())),
                onRetry = {},
                onOpenPrivacyPolicy = { uri ->
                    openedUrl = uri.toString()
                    true
                }
            )
        }

        composeRule.onNodeWithText("隐私政策").performScrollTo().performClick()
        assertEquals("https://alight77.github.io/android-habit-tracker/", openedUrl)
        composeRule.onNodeWithText("今天的坚持").assertIsDisplayed()
    }

    @Test
    fun privacyFooterShowsFeedbackWhenNoAppCanOpenUrlAtLargeFont() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp).height(700.dp).testTag("narrowStats")) {
                    StatsContent(
                        uiState = StatsUiState.Error(R.string.stats_load_failed),
                        onRetry = {},
                        onOpenPrivacyPolicy = { false }
                    )
                }
            }
        }

        val footer = composeRule.onNodeWithText("隐私政策")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .fetchSemanticsNode().boundsInRoot
        val screen = composeRule.onNodeWithTag("narrowStats")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("privacy footer is clipped on 320dp", footer.left >= screen.left && footer.right <= screen.right)
        composeRule.onNodeWithText("隐私政策").performClick()
        composeRule.onNodeWithText("无法打开隐私政策，请检查设备上的浏览器")
            .assertIsDisplayed()
    }

    @Test
    fun privacyFooterIsReachedByScrollingPastStatsInsteadOfStayingOnScreen() {
        composeRule.setContent {
            Box(Modifier.width(320.dp).height(500.dp)) {
                StatsContent(
                    uiState = StatsUiState.Success(StatsSummary(1, 0, 0, 0, 0, emptyList())),
                    onRetry = {},
                    onOpenPrivacyPolicy = { true }
                )
            }
        }

        composeRule.onNodeWithText("隐私政策").assertIsNotDisplayed()
        composeRule.onNodeWithText("隐私政策").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noHabitsShowsGuidanceInsteadOfZeroFootprint() {
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Success(
                    StatsSummary(
                        totalHabits = 0,
                        todayDoneCount = 0,
                        totalDoneCount = 0,
                        recentSevenDayGoalPercent = 0,
                        longestStreak = 0,
                        recentSevenDayCounts = (6L downTo 0L).map { daysAgo ->
                            DailyCheckInCount(LocalDate.of(2026, 10, 11).minusDays(daysAgo), 0)
                        }
                    )
                ),
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("从一个小习惯开始").assertIsDisplayed()
        composeRule.onNodeWithText("切换到今日，创建第一个习惯").assertIsDisplayed()
        composeRule.onNodeWithText("近 7 天打卡足迹").assertDoesNotExist()
    }

    @Test
    fun loadingShowsSharedStatusLabelWithoutMetrics() {
        composeRule.setContent {
            StatsContent(uiState = StatsUiState.Loading, onRetry = {})
        }

        composeRule.onNodeWithText("加载中…").assertIsDisplayed()
        composeRule.onNodeWithText("今天的坚持").assertDoesNotExist()
    }

    @Test
    fun habitWithNoCheckInsStillShowsSevenDayFootprint() {
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Success(
                    StatsSummary(
                        totalHabits = 1,
                        todayDoneCount = 0,
                        totalDoneCount = 0,
                        recentSevenDayGoalPercent = 0,
                        longestStreak = 0,
                        recentSevenDayCounts = (6L downTo 0L).map { daysAgo ->
                            DailyCheckInCount(LocalDate.of(2026, 10, 11).minusDays(daysAgo), 0)
                        }
                    )
                ),
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("近 7 天打卡足迹").assertIsDisplayed()
        composeRule.onNodeWithText("切换到今日，创建第一个习惯").assertDoesNotExist()
    }

    @Test
    fun errorReplacesPreviouslyDisplayedSuccessMetrics() {
        var state by mutableStateOf<StatsUiState>(
            StatsUiState.Success(
                StatsSummary(1, 0, 0, 0, 0, emptyList())
            )
        )
        composeRule.setContent {
            StatsContent(uiState = state, onRetry = {})
        }

        composeRule.onNodeWithText("今天的坚持").assertIsDisplayed()
        composeRule.runOnUiThread {
            state = StatsUiState.Error(R.string.stats_load_failed)
        }
        composeRule.onNodeWithText("加载统计数据失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("今天的坚持").assertDoesNotExist()
    }

    @Test
    fun sameErrorStateUsesCurrentUiLocaleWithoutRetrying() {
        val database = Room.inMemoryDatabaseBuilder(
            composeRule.activity,
            HabitDatabase::class.java
        ).build()
        val subscriptionCount = AtomicInteger()
        val failingDao = object : HabitDao by database.habitDao() {
            override fun getAllHabits(): Flow<List<HabitEntity>> = flow {
                subscriptionCount.incrementAndGet()
                error("habit source unavailable")
            }
        }
        val viewModel = StatsViewModel(
            habitDao = failingDao,
            recordDao = database.recordDao(),
            todaySource = flowOf(LocalDate.of(2026, 10, 11))
        )
        var locale by mutableStateOf(Locale.forLanguageTag("zh-CN"))
        try {
            composeRule.setContent {
                val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                    setLocale(locale)
                }
                val localizedContext = composeRule.activity.createConfigurationContext(configuration)
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides configuration
                ) {
                    StatsScreen(viewModel)
                }
            }
            composeRule.waitUntil(10_000) { viewModel.uiState.value is StatsUiState.Error }
            val errorState = viewModel.uiState.value

            listOf(
                "zh-CN" to "加载统计数据失败，请重试",
                "en-US" to "Failed to load statistics. Please try again.",
                "en-GB" to "Failed to load statistics. Please try again.",
                "ja-JP" to "Failed to load statistics. Please try again.",
                "fr-FR" to "Failed to load statistics. Please try again."
            ).forEach { (languageTag, expected) ->
                composeRule.runOnUiThread { locale = Locale.forLanguageTag(languageTag) }
                composeRule.onNodeWithText(expected).assertIsDisplayed()
                composeRule.onNodeWithText(if (languageTag == "zh-CN") "重试" else "Retry")
                    .assertIsDisplayed()
                assertSame("UI Locale change replaced the Error State", errorState, viewModel.uiState.value)
                assertEquals("UI Locale change resubscribed to the DAO", 1, subscriptionCount.get())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun englishErrorAndRetryFit320dpAtDoubleFontScale() {
        var retryCount = 0
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(Locale.US)
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 2f)
            ) {
                Box(Modifier.width(320.dp).height(700.dp).testTag("englishErrorViewport")) {
                    StatsContent(
                        uiState = StatsUiState.Error(R.string.stats_load_failed),
                        onRetry = { retryCount += 1 }
                    )
                }
            }
        }

        val body = composeRule.onNodeWithText("Failed to load statistics. Please try again.")
            .performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag("englishErrorViewport")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("English error body is clipped", body.left >= viewport.left &&
            body.right <= viewport.right && body.top >= viewport.top && body.bottom <= viewport.bottom)
        composeRule.onNodeWithText("Retry").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun summaryLabelsRecentRateAsGoalAchievement() {
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Success(
                    StatsSummary(
                        totalHabits = 1,
                        todayDoneCount = 1,
                        totalDoneCount = 1,
                        recentSevenDayGoalPercent = 100,
                        longestStreak = 1,
                        recentSevenDayCounts = (6L downTo 0L).map { daysAgo ->
                            DailyCheckInCount(
                                LocalDate.of(2026, 10, 11).minusDays(daysAgo),
                                if (daysAgo == 0L) 1 else 0
                            )
                        }
                    )
                ),
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("近 7 天目标达成率").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("近 7 天目标达成率，100%")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun successShowsDailyCountsAndDistinctHistoryMetrics() {
        val counts = listOf(1, 3, 2, 0, 2, 3, 2)
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Success(
                    StatsSummary(
                        totalHabits = 3,
                        todayDoneCount = 2,
                        totalDoneCount = 18,
                        recentSevenDayGoalPercent = 71,
                        longestStreak = 5,
                        recentSevenDayCounts = counts.mapIndexed { index, count ->
                            DailyCheckInCount(
                                LocalDate.of(2026, 10, 5).plusDays(index.toLong()),
                                count
                            )
                        }
                    )
                ),
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("成长记录").assertIsDisplayed()
        composeRule.onNodeWithText("今天的坚持").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("今天已完成 2 个习惯，共 3 个")
            .assertIsDisplayed()
        composeRule.onNodeWithText("近 7 天打卡足迹").assertIsDisplayed()
        listOf(
            "2026年10月5日，星期一，1 次打卡", "2026年10月6日，星期二，3 次打卡",
            "2026年10月7日，星期三，2 次打卡", "2026年10月8日，星期四，0 次打卡",
            "2026年10月9日，星期五，2 次打卡", "2026年10月10日，星期六，3 次打卡",
            "今天，2026年10月11日，星期日，2 次打卡"
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        composeRule.onNodeWithText("71%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("现有习惯累计打卡，18 次")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("单项习惯历史最长连续，5 天")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun chartWeekdaysAndFullDateDescriptionsFollowUiLanguageAndDataDate() {
        var locale by mutableStateOf(Locale.forLanguageTag("zh-CN"))
        var chartToday by mutableStateOf(LocalDate.of(2027, 1, 3))
        var todayCount by mutableIntStateOf(0)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration
            ) {
                val dates = (6L downTo 0L).map { chartToday.minusDays(it) }
                // 周一场景把今天放在首位，验证标记来自日期而非列表最后一项。
                val orderedDates = if (chartToday.dayOfWeek.value == 1) dates.reversed() else dates
                StatsContent(
                    uiState = StatsUiState.Success(
                        StatsSummary(
                            totalHabits = 7,
                            todayDoneCount = todayCount,
                            totalDoneCount = todayCount,
                            recentSevenDayGoalPercent = 0,
                            longestStreak = 0,
                            recentSevenDayCounts = orderedDates.map { date ->
                                DailyCheckInCount(date, if (date == chartToday) todayCount else 0)
                            }
                        )
                    ),
                    onRetry = {}
                )
            }
        }

        listOf(
            "zh-CN", "en-US", "en-GB", "ja-JP", "fr-FR"
        ).forEach { languageTag ->
            val chinese = languageTag == "zh-CN"
            listOf(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 4)).forEach { date ->
                listOf(0, 1, 2, 7).forEach { count ->
                    composeRule.runOnUiThread {
                        locale = Locale.forLanguageTag(languageTag)
                        chartToday = date
                        todayCount = count
                    }
                    val weekday = if (date.dayOfWeek.value == 1) "Monday" else "Sunday"
                    val chineseWeekday = if (date.dayOfWeek.value == 1) "星期一" else "星期日"
                    val description = if (chinese) {
                        "今天，2027年1月${date.dayOfMonth}日，$chineseWeekday，$count 次打卡"
                    } else {
                        "Today, $weekday, January ${date.dayOfMonth}, 2027. Check-ins: $count"
                    }
                    composeRule.onNodeWithContentDescription(description)
                        .performScrollTo()
                        .assertIsDisplayed()
                    if (!chinese) {
                        composeRule.onNodeWithText(if (date.dayOfWeek.value == 1) "Mo" else "Su",
                            useUnmergedTree = true).assertExists()
                        composeRule.onNodeWithTag("stats-chart-today-marker", useUnmergedTree = true)
                            .assertExists()
                    }
                }
                val yearBoundaryDay = if (chinese) {
                    "2026年12月31日，星期四，0 次打卡"
                } else {
                    "Thursday, December 31, 2026. Check-ins: 0"
                }
                composeRule.onNodeWithContentDescription(yearBoundaryDay)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
        composeRule.onNodeWithContentDescription(
            "Today, Tuesday, December 29, 2026. Check-ins: 0"
        ).assertDoesNotExist()
    }

    @Test
    fun chartLabelsFitNarrowAndLargeTextVisualMatrix() {
        var locale by mutableStateOf(Locale.US)
        var width by mutableIntStateOf(360)
        var fontScale by mutableStateOf(1f)
        var dark by mutableStateOf(false)
        val today = LocalDate.of(2026, 10, 11)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(deviceDensity, fontScale)
            ) {
                key(locale.toLanguageTag(), width, fontScale, dark) {
                    HabitTrackerTheme(darkTheme = dark) {
                        Box(Modifier.width(width.dp).height(700.dp).testTag("chartVisualHost")) {
                            StatsContent(
                                uiState = StatsUiState.Success(
                                    StatsSummary(
                                        totalHabits = 7,
                                        todayDoneCount = 2,
                                        totalDoneCount = 12,
                                        recentSevenDayGoalPercent = 71,
                                        longestStreak = 5,
                                        recentSevenDayCounts = (6L downTo 0L).map { daysAgo ->
                                            DailyCheckInCount(
                                                today.minusDays(daysAgo),
                                                listOf(1, 3, 2, 0, 2, 3, 2)[(6 - daysAgo).toInt()]
                                            )
                                        }
                                    )
                                ),
                                onRetry = {}
                            )
                        }
                    }
                }
            }
        }

        data class VisualCase(val name: String, val locale: Locale, val width: Int,
                              val fontScale: Float, val dark: Boolean)
        val cases = listOf(
            VisualCase("en-360-100-light", Locale.US, 360, 1f, false),
            VisualCase("en-320-100-light", Locale.US, 320, 1f, false),
            VisualCase("en-320-200-light", Locale.US, 320, 2f, false),
            VisualCase("en-320-200-dark", Locale.US, 320, 2f, true),
            VisualCase("zh-320-200-light", Locale.SIMPLIFIED_CHINESE, 320, 2f, false)
        )
        val screenshotDir = File(composeRule.activity.getExternalFilesDir(null), "i18n-b26")
        assertTrue(screenshotDir.exists() || screenshotDir.mkdirs())
        cases.forEach { case ->
            composeRule.runOnUiThread {
                locale = case.locale
                width = case.width
                fontScale = case.fontScale
                dark = case.dark
            }
            val todayDescription = if (case.locale.language == "zh") {
                "今天，2026年10月11日，星期日，2 次打卡"
            } else {
                "Today, Sunday, October 11, 2026. Check-ins: 2"
            }
            composeRule.onNodeWithText(
                if (case.locale.language == "zh") "近 7 天打卡足迹"
                else "Check-in history: last 7 days"
            ).performScrollTo()
            if (case.fontScale >= 1.5f) {
                composeRule.onNodeWithTag("chartVisualHost").performTouchInput {
                    swipeUp(startY = bottom * 0.75f, endY = bottom * 0.45f)
                }
            }
            val host = composeRule.onNodeWithTag("chartVisualHost")
                .fetchSemanticsNode().boundsInRoot
            val labels = if (case.locale.language == "zh") {
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "今天")
            } else {
                listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
            }
            val weekdays = if (case.locale.language == "zh") {
                listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            } else {
                listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            }
            val counts = listOf(1, 3, 2, 0, 2, 3, 2)
            labels.forEachIndexed { index, labelText ->
                val date = today.minusDays((6 - index).toLong())
                val description = if (case.locale.language == "zh") {
                    "${if (index == 6) "今天，" else ""}2026年10月${date.dayOfMonth}日，" +
                        "${weekdays[index]}，${counts[index]} 次打卡"
                } else {
                    "${if (index == 6) "Today, " else ""}${weekdays[index]}, " +
                        "October ${date.dayOfMonth}, 2026. Check-ins: ${counts[index]}"
                }
                composeRule.onNodeWithContentDescription(description)
                    .performScrollTo()
                    .assertIsDisplayed()
                val labelBounds = composeRule.onNodeWithText(labelText, useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("${case.name}: $labelText left clipped", labelBounds.left >= host.left - 1f)
                assertTrue("${case.name}: $labelText right clipped", labelBounds.right <= host.right + 1f)
            }
            composeRule.onNodeWithContentDescription(todayDescription).performScrollTo()
            val image = composeRule.onNodeWithTag("chartVisualHost").captureToImage()
                .asAndroidBitmap()
            File(screenshotDir, "${case.name}.png").outputStream().use { output ->
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            composeRule.onNodeWithContentDescription(todayDescription)
                .assertIsDisplayed()
        }
    }

    @Test
    fun historyMetricUnitsMatchCountsForChineseEnglishAndEnglishFallback() {
        var locale by mutableStateOf(Locale.US)
        var count by mutableIntStateOf(0)
        composeRule.setContent {
            val configuration = Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = composeRule.activity.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration
            ) {
                StatsContent(
                    uiState = StatsUiState.Success(
                        StatsSummary(
                            totalHabits = 1,
                            todayDoneCount = 0,
                            totalDoneCount = count,
                            recentSevenDayGoalPercent = 0,
                            longestStreak = count,
                            recentSevenDayCounts = emptyList()
                        )
                    ),
                    onRetry = {}
                )
            }
        }

        val chinese = listOf(
            0 to ("现有习惯累计打卡，0 次" to "单项习惯历史最长连续，0 天"),
            1 to ("现有习惯累计打卡，1 次" to "单项习惯历史最长连续，1 天"),
            2 to ("现有习惯累计打卡，2 次" to "单项习惯历史最长连续，2 天"),
            7 to ("现有习惯累计打卡，7 次" to "单项习惯历史最长连续，7 天")
        )
        val english = listOf(
            0 to ("Total check-ins for current habits: 0 check-ins" to "Longest streak for a single habit: 0 days"),
            1 to ("Total check-ins for current habits: 1 check-in" to "Longest streak for a single habit: 1 day"),
            2 to ("Total check-ins for current habits: 2 check-ins" to "Longest streak for a single habit: 2 days"),
            7 to ("Total check-ins for current habits: 7 check-ins" to "Longest streak for a single habit: 7 days")
        )

        listOf(
            Locale.forLanguageTag("zh-CN") to chinese,
            Locale.forLanguageTag("en-US") to english,
            Locale.forLanguageTag("ja-JP") to english,
            Locale.forLanguageTag("fr-FR") to english
        ).forEach { (testLocale, cases) ->
            cases.forEach { (value, descriptions) ->
                composeRule.runOnUiThread {
                    locale = testLocale
                    count = value
                }
                composeRule.onNodeWithContentDescription(descriptions.first)
                    .performScrollTo()
                    .assertIsDisplayed()
                composeRule.onNodeWithContentDescription(descriptions.second)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun longNumbersStayInside320dpAtDoubleFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp).height(700.dp).testTag("narrowStats")) {
                    StatsContent(
                        uiState = StatsUiState.Success(
                            StatsSummary(
                                totalHabits = 10000,
                                todayDoneCount = 9999,
                                totalDoneCount = 1234567,
                                recentSevenDayGoalPercent = 71,
                                longestStreak = 234567,
                                recentSevenDayCounts = (6L downTo 0L).map { daysAgo ->
                                    DailyCheckInCount(
                                        LocalDate.of(2026, 10, 11).minusDays(daysAgo),
                                        if (daysAgo == 0L) 9999 else 1
                                    )
                                }
                            )
                        ),
                        onRetry = {}
                    )
                }
            }
        }

        val rightEdge = composeRule.onNodeWithTag("narrowStats")
            .fetchSemanticsNode().boundsInRoot.right -
            24f * composeRule.activity.resources.displayMetrics.density
        val todayBounds = composeRule
            .onNodeWithContentDescription("今天已完成 9999 个习惯，共 10000 个")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("today count exceeds 320dp", todayBounds.right <= rightEdge + 1f)
        composeRule.onNodeWithContentDescription("今天，2026年10月11日，星期日，9999 次打卡")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("近 7 天目标达成率，71%")
            .performScrollTo()
            .assertIsDisplayed()
        listOf("1234567", "234567").forEach { value ->
            composeRule.onNodeWithText(value, useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
            val bounds = composeRule.onNodeWithText(value, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$value exceeds 320dp", bounds.right <= rightEdge + 1f)
        }
    }

    @Test
    fun topLevelStatsHasNoBackButton() {
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Loading,
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("返回").assertDoesNotExist()
        composeRule.onNodeWithText("成长记录").assertIsDisplayed()
    }
}
