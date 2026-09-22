package com.example.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habittracker.domain.usecase.RecentGoalProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DashboardContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
                            goalProgress = RecentGoalProgress(1, 3)
                        )
                    )
                ),
                onCheckClick = { _, _ -> },
                onAddClick = {},
                onStatsClick = {},
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
        composeRule.onNodeWithText("确定删除“阅读”吗？").assertIsDisplayed()
        assertEquals(0, deletedHabitId.get())

        composeRule.onNodeWithText("确认").performClick()
        assertEquals(7, deletedHabitId.get())
    }

    @Test
    fun addAction_exposesContentDescriptionAndInvokesCallback() {
        val addClickCount = AtomicInteger(0)
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Success(emptyList()),
                onCheckClick = { _, _ -> },
                onAddClick = { addClickCount.incrementAndGet() },
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("新增习惯").performClick()

        assertEquals(1, addClickCount.get())
    }
}
