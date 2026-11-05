package io.github.alight77.habittracker.ui.component

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class HabitFormContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun weeklyGoalGuidanceIsAccurateForOneFiveAndSevenInBothLocales() {
        val target = mutableIntStateOf(1)
        val locale = mutableStateOf(Locale.US)
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(locale.value)
            }
            val localizedContext = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration
            ) {
                key(locale.value.toLanguageTag()) {
                    HabitTrackerTheme {
                        HabitFormContent(
                            name = "Reading",
                            targetPerWeek = target.intValue,
                            nameError = null,
                            isSaving = false,
                            onNameChanged = {},
                            onDecreaseTarget = {},
                            onIncreaseTarget = {},
                            onSave = {},
                            saveLabel = "Save"
                        )
                    }
                }
            }
        }

        val cases = listOf(
            Triple(Locale.US, 1, "Check in on any days to reach your goal of 1 per week."),
            Triple(Locale.US, 5, "Check in on any days to reach your goal of 5 per week."),
            Triple(Locale.US, 6, "Check in on any days to reach your goal of 6 per week."),
            Triple(Locale.US, 7, "A goal of 7 per week means checking in every day."),
            Triple(Locale.SIMPLIFIED_CHINESE, 1, "每周计划 1 次，可以灵活安排时间。"),
            Triple(Locale.SIMPLIFIED_CHINESE, 5, "每周计划 5 次，可以灵活安排时间。"),
            Triple(Locale.SIMPLIFIED_CHINESE, 7, "每周计划 7 次，目标是每天一次。")
        )
        cases.forEach { (caseLocale, caseTarget, guidance) ->
            composeRule.runOnUiThread {
                locale.value = caseLocale
                target.intValue = caseTarget
            }
            composeRule.onNodeWithText(guidance).assertExists()
            val value = if (caseLocale.language == "zh") "$caseTarget 次 / 周" else "$caseTarget per week"
            composeRule.onNodeWithText(value).assertExists()
        }
    }

    @Test
    fun savingDisablesSnapshotFieldsAndSaveAction() {
        composeRule.setContent {
            HabitTrackerTheme {
                HabitFormContent(
                    name = "阅读",
                    targetPerWeek = 5,
                    nameError = null,
                    isSaving = true,
                    onNameChanged = {},
                    onDecreaseTarget = {},
                    onIncreaseTarget = {},
                    onSave = {},
                    saveLabel = "保存修改",
                    showNameSuggestions = true,
                    iconStatus = "图标 · 当前：阅读",
                    iconActionLabel = "按名称推荐"
                )
            }
        }

        composeRule.onNodeWithTag("habit_name_input").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("减少每周目标次数").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("增加每周目标次数").assertIsNotEnabled()
        composeRule.onNodeWithTag("habit_name_suggestion_0").assertIsNotEnabled()
        composeRule.onNodeWithTag("habit_choose_icon").assertIsNotEnabled()
        composeRule.onNodeWithTag("habit_icon_action").assertIsNotEnabled()
        composeRule.onNodeWithText("保存中…").assertIsNotEnabled()
    }

    @Test
    fun previewBadgeAndCard_areNotInteractive() {
        composeRule.setContent {
            HabitTrackerTheme {
                HabitPreviewCard(name = "阅读 30 分钟", targetPerWeek = 5)
            }
        }

        composeRule.onNodeWithTag("habit_preview_card").assertHasNoClickAction()
        composeRule.onNodeWithText("效果预览").assertHasNoClickAction()
    }
}
