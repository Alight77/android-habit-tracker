package io.github.alight77.habittracker.feature.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
