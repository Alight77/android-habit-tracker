package com.example.habittracker.feature.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                        longestStreak = 1
                    )
                ),
                onBackClick = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("近 7 天目标达成率").assertIsDisplayed()
    }
}
