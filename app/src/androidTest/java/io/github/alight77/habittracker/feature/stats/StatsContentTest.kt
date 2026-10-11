package io.github.alight77.habittracker.feature.stats

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
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
                onBackClick = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("近 7 天目标达成率").assertIsDisplayed()
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
                onBackClick = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("成长记录").assertIsDisplayed()
        composeRule.onNodeWithText("今天的坚持").assertIsDisplayed()
        composeRule.onNodeWithText("近 7 天打卡足迹").assertIsDisplayed()
        listOf(
            "周一，1 次打卡", "周二，3 次打卡", "周三，2 次打卡", "周四，0 次打卡",
            "周五，2 次打卡", "周六，3 次打卡", "今天，2 次打卡"
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        composeRule.onNodeWithText("71%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("现有习惯累计打卡").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("单项习惯历史最长连续").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("18").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("5").performScrollTo().assertIsDisplayed()
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
                        onBackClick = {},
                        onRetry = {}
                    )
                }
            }
        }

        val rightEdge = composeRule.onNodeWithTag("narrowStats")
            .fetchSemanticsNode().boundsInRoot.right -
            24f * composeRule.activity.resources.displayMetrics.density
        listOf("9999", "10000").forEach { value ->
            val bounds = composeRule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
            assertTrue("$value exceeds 320dp", bounds.right <= rightEdge + 1f)
        }
        listOf("1234567", "234567").forEach { value ->
            composeRule.onNodeWithText(value).performScrollTo().assertIsDisplayed()
            val bounds = composeRule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
            assertTrue("$value exceeds 320dp", bounds.right <= rightEdge + 1f)
        }
    }

    @Test
    fun backButton_exposesContentDescription() {
        composeRule.setContent {
            StatsContent(
                uiState = StatsUiState.Loading,
                onBackClick = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
    }
}
