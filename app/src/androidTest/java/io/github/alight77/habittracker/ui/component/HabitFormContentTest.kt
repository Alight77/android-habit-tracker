package io.github.alight77.habittracker.ui.component

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import org.junit.Rule
import org.junit.Test

class HabitFormContentTest {

    @get:Rule
    val composeRule = createComposeRule()

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
