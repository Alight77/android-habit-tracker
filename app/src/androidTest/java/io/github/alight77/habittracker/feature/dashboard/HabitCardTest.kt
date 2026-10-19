package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HabitCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun targetAndStreak_arePresentedOnTheSameInformationRow() {
        setChineseContent {
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
                    today = LocalDate.of(2026, 10, 11),
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
    fun knownIconKey_usesItsCategoryImageWithoutChangingCheckAction() {
        setHabitCardContent(isDoneToday = false, iconKey = IconKey.READING)

        composeRule.onNodeWithTag("habit_card_icon_reading", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打卡：阅读").assertHasClickAction()
    }

    @Test
    fun unknownIconKey_usesGeneralImageWithoutChangingCompletionState() {
        setHabitCardContent(isDoneToday = true, iconKey = IconKey("cycling"))

        composeRule.onNodeWithTag("habit_card_icon_general", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已完成：阅读")
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

        composeRule.onNodeWithContentDescription("2026年10月8日，周四：已打卡", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2026年10月10日，周六：未打卡", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent-check-in-day", useUnmergedTree = true)
            .assertCountEquals(7)
        composeRule.onNodeWithText("今", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("✓", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun longNameAndLargeStreakRemainReadableAt320dpAnd2xFont() {
        setChineseContent {
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
                            today = LocalDate.of(2026, 10, 11),
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

    @Test
    fun sevenDayLabelsAndTodayMarkerFollowResourceLanguageAndCheckState() {
        val today = LocalDate.of(2026, 10, 11)
        val displayedLocale = mutableStateOf(Locale.US)
        val doneToday = mutableStateOf(false)
        composeRule.setContent {
            WithLocale(displayedLocale.value) {
                HabitTrackerTheme {
                    HabitCard(
                        habit = HabitItemUiState(
                            id = 1,
                            name = "Read",
                            targetPerWeek = 3,
                            isDoneToday = doneToday.value,
                            streak = if (doneToday.value) 1 else 0,
                            recentDays = recentCheckInDays(
                                today.minusDays(6),
                                if (doneToday.value) listOf(today) else emptyList(),
                                today
                            )
                        ),
                        today = today,
                        onCheckClick = {},
                        onEditClick = {},
                        onDeleteClick = {}
                    )
                }
            }
        }

        val locales = listOf(
            Locale.forLanguageTag("zh-CN") to true,
            Locale.forLanguageTag("en-US") to false,
            Locale.forLanguageTag("en-GB") to false,
            Locale.forLanguageTag("ja-JP") to false,
            Locale.forLanguageTag("fr-FR") to false
        )
        for ((locale, chinese) in locales) {
            for (checked in listOf(false, true)) {
                composeRule.runOnUiThread {
                    displayedLocale.value = locale
                    doneToday.value = checked
                }
                val labels = if (chinese) listOf("一", "二", "三", "四", "五", "六", "今")
                else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                labels.forEach { label ->
                    composeRule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                }
                composeRule.onAllNodesWithTag("recent-check-in-day", useUnmergedTree = true)
                    .assertCountEquals(7)
                composeRule.onAllNodesWithTag("recent-check-in-today-marker", useUnmergedTree = true)
                    .assertCountEquals(if (chinese) 0 else 1)
                val expectedToday = when {
                    chinese && checked -> "2026年10月11日，周日，今天：已打卡"
                    chinese -> "2026年10月11日，周日：今日尚未打卡"
                    checked -> "Sunday, October 11, 2026 (today): Checked in"
                    else -> "Sunday, October 11, 2026: Not checked in today"
                }
                composeRule.onNodeWithContentDescription(expectedToday, substring = true)
                    .assertIsDisplayed()
                if (!chinese) {
                    composeRule.onNodeWithText("Today", useUnmergedTree = true).assertDoesNotExist()
                    composeRule.onNodeWithText("今", useUnmergedTree = true).assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun crossYearDescriptionKeepsBeforeCreationAndTodayFollowsDateRatherThanPosition() {
        val today = LocalDate.of(2027, 1, 2)
        val orderedDays = recentCheckInDays(
            createdDate = LocalDate.of(2027, 1, 1),
            doneDates = listOf(LocalDate.of(2027, 1, 1)),
            today = today
        )
        val displayedDays = mutableStateOf(orderedDays)
        composeRule.setContent {
            WithLocale(Locale.US) {
                HabitTrackerTheme {
                    HabitCard(
                        habit = HabitItemUiState(
                            id = 1,
                            name = "Read",
                            targetPerWeek = 3,
                            isDoneToday = false,
                            streak = 0,
                            recentDays = displayedDays.value
                        ),
                        today = today,
                        onCheckClick = {},
                        onEditClick = {},
                        onDeleteClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Sunday, December 27, 2026: Before creation", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Friday, January 1, 2027: Checked in", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Saturday, January 2, 2027: Not checked in today", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent-check-in-today-marker", useUnmergedTree = true)
            .assertCountEquals(1)

        // 故意把今天移到倒数第二格，确认标记与无障碍文案依据日期而非最后一格。
        composeRule.runOnUiThread {
            displayedDays.value = orderedDays.dropLast(2) + orderedDays.last() + orderedDays[5]
        }
        composeRule.onNodeWithContentDescription("Saturday, January 2, 2027: Not checked in today", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent-check-in-today-marker", useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.runOnUiThread { displayedDays.value = emptyList() }
        composeRule.onAllNodesWithTag("recent-check-in-day", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("recent-check-in-today-marker", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun englishCompletedCardKeepsSevenDayStripAndMarkerClearAt320dpAnd2xFont() {
        val today = LocalDate.of(2026, 10, 11)
        val name = "Read technical articles during my commute"
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            WithLocale(Locale.US) {
                CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, 2f)) {
                    HabitTrackerTheme {
                        Box(Modifier.width(320.dp).testTag("cardArea")) {
                            HabitCard(
                                habit = HabitItemUiState(
                                    id = 1,
                                    name = name,
                                    targetPerWeek = 7,
                                    isDoneToday = true,
                                    streak = 1,
                                    recentDays = recentCheckInDays(today.minusDays(6), listOf(today), today)
                                ),
                                today = today,
                                onCheckClick = {},
                                onEditClick = {},
                                onDeleteClick = {}
                            )
                        }
                    }
                }
            }
        }

        val area = composeRule.onNodeWithTag("cardArea").fetchSemanticsNode().boundsInRoot
        val squares = composeRule.onAllNodesWithTag("recent-check-in-day", useUnmergedTree = true)
            .fetchSemanticsNodes()
        val marker = composeRule.onNodeWithTag("recent-check-in-today-marker", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val completed = composeRule.onNodeWithContentDescription("Completed: $name")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(7, squares.size)
        squares.forEach { square ->
            assertTrue(square.boundsInRoot.left >= area.left - 1f)
            assertTrue(square.boundsInRoot.right <= area.right + 1f)
        }
        assertTrue(marker.left >= area.left - 1f && marker.right <= area.right + 1f)
        assertTrue(marker.bottom < completed.top)
        composeRule.onNodeWithText("Su", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun setHabitCardContent(
        isDoneToday: Boolean,
        iconKey: IconKey = IconKey.GENERAL,
        targetPerWeek: Int = 3,
        streak: Int = if (isDoneToday) 1 else 0,
        recentDays: List<RecentCheckInDay> = recentCheckInDays(
            LocalDate.of(2026, 10, 11),
            if (isDoneToday) listOf(LocalDate.of(2026, 10, 11)) else emptyList(),
            LocalDate.of(2026, 10, 11)
        )
    ) {
        setChineseContent {
            HabitTrackerTheme {
                HabitCard(
                    habit = HabitItemUiState(
                        id = 1,
                        name = "阅读",
                        iconKey = iconKey,
                        targetPerWeek = targetPerWeek,
                        isDoneToday = isDoneToday,
                        streak = streak,
                        recentDays = recentDays
                    ),
                    today = LocalDate.of(2026, 10, 11),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
            }
        }
    }

    private fun setChineseContent(content: @Composable () -> Unit) {
        composeRule.setContent { WithLocale(Locale.SIMPLIFIED_CHINESE, content) }
    }

    @Composable
    private fun WithLocale(locale: Locale, content: @Composable () -> Unit) {
        val configuration = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = composeRule.activity.createConfigurationContext(configuration)
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides configuration
        ) {
            content()
        }
    }
}
