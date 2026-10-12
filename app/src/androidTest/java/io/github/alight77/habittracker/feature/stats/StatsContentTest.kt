package io.github.alight77.habittracker.feature.stats

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StatsContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
            state = StatsUiState.Error("加载统计数据失败，请重试")
        }
        composeRule.onNodeWithText("加载统计数据失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("今天的坚持").assertDoesNotExist()
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
            "周一，1 次打卡", "周二，3 次打卡", "周三，2 次打卡", "周四，0 次打卡",
            "周五，2 次打卡", "周六，3 次打卡", "今天，2 次打卡"
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
        composeRule.onNodeWithContentDescription("今天，9999 次打卡")
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
