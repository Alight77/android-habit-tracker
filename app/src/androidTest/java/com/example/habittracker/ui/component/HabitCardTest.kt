package com.example.habittracker.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habittracker.feature.dashboard.HabitItemUiState
import com.example.habittracker.ui.theme.HabitTrackerTheme
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
                        streak = 2
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
}
