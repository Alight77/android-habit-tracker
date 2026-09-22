package io.github.alight77.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.domain.usecase.RecentGoalProgress
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun targetAndStreak_arePresentedOnTheSameInformationRow() {
        composeRule.setContent {
            HabitTrackerTheme {
                HabitCard(
                    habit = HabitItemUiState(
                        id = 1,
                        name = "阅读",
                        targetPerWeek = 3,
                        isDoneToday = false,
                        streak = 2,
                        goalProgress = RecentGoalProgress(1, 3)
                    ),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
            }
        }

        val targetBounds = composeRule.onNodeWithText("每周目标 3 次").getUnclippedBoundsInRoot()
        val streakBounds = composeRule.onNodeWithText("连续 2 天").getUnclippedBoundsInRoot()

        assertEquals(targetBounds.top.value, streakBounds.top.value, 1f)
        assertTrue(targetBounds.left.value < streakBounds.left.value)
    }

    @Test
    fun uncheckedHabit_exposesCheckInActionAndState() {
        setHabitCardContent(isDoneToday = false)

        composeRule.onNodeWithText("打卡", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打卡：阅读")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未完成"))
    }

    @Test
    fun completedHabit_exposesCompletedActionAndState() {
        setHabitCardContent(isDoneToday = true)

        composeRule.onNodeWithText("✓", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("已完成", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已完成：阅读")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已完成"))
    }

    @Test
    fun cardShowsRecentGoalProgress() {
        setHabitCardContent(isDoneToday = false)

        composeRule.onNodeWithText("近7天进度 1/3").assertIsDisplayed()
    }

    private fun setHabitCardContent(isDoneToday: Boolean) {
        composeRule.setContent {
            HabitTrackerTheme {
                HabitCard(
                    habit = HabitItemUiState(
                        id = 1,
                        name = "阅读",
                        targetPerWeek = 3,
                        isDoneToday = isDoneToday,
                        streak = 2,
                        goalProgress = RecentGoalProgress(1, 3)
                    ),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
            }
        }
    }
}
