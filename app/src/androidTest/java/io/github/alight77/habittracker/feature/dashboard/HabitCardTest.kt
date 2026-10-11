package io.github.alight77.habittracker.feature.dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

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
                        recentDays = recentCheckInDays(
                            LocalDate.of(2026, 10, 5),
                            listOf(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10)),
                            LocalDate.of(2026, 10, 11)
                        )
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
        composeRule.onNodeWithText("✓", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("打卡：阅读")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未完成"))
    }

    @Test
    fun completedHabit_exposesCompletedActionAndState() {
        setHabitCardContent(isDoneToday = true)

        composeRule.onNodeWithText("已完成", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("✓", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已完成：阅读")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已完成"))
    }

    @Test
    fun cardShowsTargetAndStreakWithoutDuplicateCaptions() {
        setHabitCardContent(isDoneToday = false)

        composeRule.onNodeWithText("每周目标 3 次").assertIsDisplayed()
        composeRule.onNodeWithText("连续 0 天").assertIsDisplayed()
        composeRule.onNodeWithText("连续打卡进度 0/3").assertDoesNotExist()
        composeRule.onNodeWithText("近 7 天打卡", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("近 7 天打卡记录", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun newHabitStartsAtZeroOfFullTarget() {
        setHabitCardContent(
            isDoneToday = false,
            targetPerWeek = 7
        )

        composeRule.onNodeWithText("每周目标 7 次").assertIsDisplayed()
        composeRule.onNodeWithText("连续 0 天").assertIsDisplayed()
    }

    @Test
    fun firstCheckUpdatesStreakWhileKeepingFullTarget() {
        setHabitCardContent(isDoneToday = true, targetPerWeek = 7)
        composeRule.onNodeWithText("每周目标 7 次").assertIsDisplayed()
        composeRule.onNodeWithText("连续 1 天").assertIsDisplayed()
    }

    @Test
    fun zeroTargetDoesNotShowInvalidRatio() {
        setHabitCardContent(
            isDoneToday = false,
            targetPerWeek = 0
        )

        composeRule.onNodeWithText("每周目标 0 次").assertIsDisplayed()
        composeRule.onNodeWithText("连续 0 天").assertIsDisplayed()
        composeRule.onNodeWithText("连续打卡进度 0/0").assertDoesNotExist()
    }

    @Test
    fun streakAboveTargetKeepsActualNumber() {
        setHabitCardContent(
            isDoneToday = true,
            targetPerWeek = 5,
            streak = 8
        )

        composeRule.onNodeWithText("每周目标 5 次").assertIsDisplayed()
        composeRule.onNodeWithText("连续 8 天").assertIsDisplayed()
    }

    @Test
    fun missedDayKeepsEarlierChecksInSevenDayDescription() {
        val today = LocalDate.of(2026, 10, 11)
        setHabitCardContent(
            isDoneToday = false,
            targetPerWeek = 7,
            recentDays = recentCheckInDays(
                today.minusDays(6),
                listOf(today.minusDays(3), today.minusDays(2)),
                today
            )
        )

        composeRule.onNodeWithContentDescription("10月8日已打卡", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("10月10日未打卡", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent-check-in-day", useUnmergedTree = true)
            .assertCountEquals(7)
        composeRule.onNodeWithText("今", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("✓", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun longNameAndLargeStreakRemainReadableAt320dpAnd2xFont() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, 2f)
            ) {
                HabitTrackerTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        HabitCard(
                            habit = HabitItemUiState(
                                id = 1,
                                name = "通勤时阅读 Android 工程实践文章",
                                targetPerWeek = 7,
                                isDoneToday = false,
                                streak = 12345,
                                recentDays = recentCheckInDays(
                                    LocalDate.of(2026, 10, 11),
                                    emptyList(),
                                    LocalDate.of(2026, 10, 11)
                                )
                            ),
                            onCheckClick = {},
                            onEditClick = {},
                            onDeleteClick = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("通勤时阅读 Android 工程实践文章", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("连续 12345 天").assertIsDisplayed()
        composeRule.onNodeWithText("每周目标 7 次").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打卡：通勤时阅读 Android 工程实践文章")
            .assertIsDisplayed()
    }

    private fun setHabitCardContent(
        isDoneToday: Boolean,
        targetPerWeek: Int = 3,
        streak: Int = if (isDoneToday) 1 else 0,
        recentDays: List<RecentCheckInDay> = recentCheckInDays(
            LocalDate.of(2026, 10, 11),
            if (isDoneToday) listOf(LocalDate.of(2026, 10, 11)) else emptyList(),
            LocalDate.of(2026, 10, 11)
        )
    ) {
        composeRule.setContent {
            HabitTrackerTheme {
                HabitCard(
                    habit = HabitItemUiState(
                        id = 1,
                        name = "阅读",
                        targetPerWeek = targetPerWeek,
                        isDoneToday = isDoneToday,
                        streak = streak,
                        recentDays = recentDays
                    ),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
            }
        }
    }
}
