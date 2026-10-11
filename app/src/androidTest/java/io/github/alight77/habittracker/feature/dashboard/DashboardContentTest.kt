package io.github.alight77.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.domain.usecase.RecentGoalProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
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
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        val emptyArea = composeRule.onNodeWithTag("dashboardEmptyArea")
            .fetchSemanticsNode().boundsInRoot
        val message = composeRule.onNodeWithText("从一个小习惯开始")
            .fetchSemanticsNode().boundsInRoot
        val headerText = composeRule.onNodeWithText("今天也从一个小习惯开始")
            .fetchSemanticsNode().boundsInRoot

        // 无习惯时提示语随页头与可用高度定位，不能紧贴页头。
        assertTrue(message.top > headerText.bottom)
        assertTrue(abs(message.center.y - emptyArea.center.y) < 2f)
    }

    @Test
    fun dateHeaderFollowsSuccessTodayForEmptyDashboard() {
        val state = mutableStateOf<DashboardUiState>(
            DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 11))
        )
        composeRule.setContent {
            DashboardContent(
                uiState = state.value,
                onCheckClick = {},
                onAddClick = {},
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("10月11日 周日").assertIsDisplayed()
        composeRule.onNodeWithText("今天也从一个小习惯开始").assertIsDisplayed()

        // 模拟既有日期来源更新；页头必须立即跟随 Success.today，而非自行读取系统时钟。
        composeRule.runOnUiThread {
            state.value = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 12))
        }

        composeRule.onNodeWithText("10月12日 周一").assertIsDisplayed()
        composeRule.onNodeWithText("10月11日 周日").assertDoesNotExist()
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
                goalProgress = RecentGoalProgress(0, 3)
            )
        }
        composeRule.setContent {
            DashboardContent(
                uiState = DashboardUiState.Success(items, LocalDate.of(2026, 10, 11)),
                onCheckClick = {},
                onAddClick = {},
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithText("10月11日 周日").assertIsDisplayed()
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
                            goalProgress = RecentGoalProgress(1, 3)
                        )
                    ),
                    today = LocalDate.of(2026, 9, 19)
                ),
                onCheckClick = { _ -> },
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
        composeRule.onNodeWithText("永久删除“阅读”及其全部历史打卡记录？删除后无法恢复。")
            .assertIsDisplayed()
        assertEquals(0, deletedHabitId.get())

        composeRule.onNodeWithText("永久删除").performClick()
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
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = {},
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("新增习惯").performClick()

        assertEquals(1, addClickCount.get())
    }

    @Test
    fun deleteDialogExplainsPermanentRecordLossAndCancelKeepsCallbackUntouched() {
        val deletedHabitId = AtomicInteger(0)
        composeRule.setContent {
            DashboardContent(
                uiState = singleHabitState(),
                onCheckClick = {},
                onAddClick = {},
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = { deletedHabitId.set(it) },
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        // 永久删除的后果必须在 TalkBack 可读取的弹窗正文中完整呈现。
        composeRule.onNodeWithText("永久删除“阅读”及其全部历史打卡记录？删除后无法恢复。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("永久删除").assertIsDisplayed()
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
                onStatsClick = {},
                onEditClick = {},
                onDeleteHabit = { deletedHabitIds += it },
                onRetry = {}
            )
        }

        composeRule.onNodeWithContentDescription("更多操作：阅读").performClick()
        composeRule.onNodeWithText("删除").performClick()
        // 同一坐标快速双击；第一击关闭弹窗后，第二击不得删除其他习惯或重复派发。
        composeRule.onNodeWithText("永久删除").performTouchInput { doubleClick() }

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
                goalProgress = RecentGoalProgress(1, 3)
            )
        ),
        today = LocalDate.of(2026, 9, 19)
    )
}
