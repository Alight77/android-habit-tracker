package io.github.alight77.habittracker.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.domain.model.WeeklyTargetConstraints.MAX_TARGET_PER_WEEK
import io.github.alight77.habittracker.domain.model.WeeklyTargetConstraints.MIN_TARGET_PER_WEEK
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitFormContent(
    name: String,
    targetPerWeek: Int,
    nameError: String?,
    isSaving: Boolean,
    onNameChanged: (String) -> Unit,
    onDecreaseTarget: () -> Unit,
    onIncreaseTarget: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String,
    showNameSuggestions: Boolean = false,
    iconKey: IconKey = IconKey.GENERAL,
    iconStatus: String = "",
    onChooseIcon: () -> Unit = {},
    iconActionLabel: String? = null,
    onIconAction: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val decreaseDescription = stringResource(R.string.decrease_weekly_goal)
    val increaseDescription = stringResource(R.string.increase_weekly_goal)
    val goalGuidance = if (targetPerWeek == MAX_TARGET_PER_WEEK) {
        stringResource(R.string.weekly_plan_every_day)
    } else {
        stringResource(R.string.weekly_plan_flexible, targetPerWeek)
    }
    val canDecrease = !isSaving && targetPerWeek > MIN_TARGET_PER_WEEK
    val canIncrease = !isSaving && targetPerWeek < MAX_TARGET_PER_WEEK
    val nameBringIntoViewRequester = remember { BringIntoViewRequester() }
    var isNameFocused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(isNameFocused, imeBottom) {
        // 首次聚焦先于键盘展开；视口随后缩小时按实际 IME Insets 再定位当前输入框。
        if (isNameFocused && imeBottom > 0) nameBringIntoViewRequester.bringIntoView()
    }
    val stepperColors = IconButtonDefaults.outlinedIconButtonColors(
        contentColor = colors.onSurface,
        disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.45f)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HabitPreviewCard(name = name, targetPerWeek = targetPerWeek, iconKey = iconKey)

        if (iconStatus.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedButton(
                    onClick = onChooseIcon,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_choose_icon")
                ) {
                    Text(iconStatus, textAlign = TextAlign.Center)
                }
                iconActionLabel?.let { label ->
                    TextButton(
                        onClick = onIconAction,
                        enabled = !isSaving,
                        modifier = Modifier.align(Alignment.End).testTag("habit_icon_action")
                    ) { Text(label) }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(nameBringIntoViewRequester)
                    .onFocusChanged { isNameFocused = it.isFocused }
                    .testTag("habit_name_input"),
                label = { Text(stringResource(R.string.habit_name)) },
                placeholder = { Text(stringResource(R.string.habit_name_example)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(14.dp),
                // 写入使用点击保存时的快照；处理中锁定所有表单入口，避免新输入被误认为已保存。
                enabled = !isSaving,
                isError = nameError != null,
                supportingText = nameError?.let { message -> { Text(message) } }
            )

            if (showNameSuggestions) {
                Text(
                    text = stringResource(R.string.habit_name_suggestions_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stringArrayResource(R.array.habit_name_suggestions).forEachIndexed { index, suggestion ->
                        FilterChip(
                            selected = name.trim() == suggestion,
                            onClick = { onNameChanged(suggestion) },
                            label = { Text(suggestion) },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("habit_name_suggestion_$index")
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.weekly_goal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedIconButton(
                    onClick = onDecreaseTarget,
                    enabled = canDecrease,
                    colors = stepperColors,
                    border = BorderStroke(
                        1.dp,
                        colors.onSurfaceVariant.copy(alpha = if (canDecrease) 1f else 0.35f)
                    ),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = decreaseDescription
                    }
                ) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }

                Text(
                    text = stringResource(R.string.weekly_goal_value, targetPerWeek),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )

                OutlinedIconButton(
                    onClick = onIncreaseTarget,
                    enabled = canIncrease,
                    colors = stepperColors,
                    border = BorderStroke(
                        1.dp,
                        colors.onSurfaceVariant.copy(alpha = if (canIncrease) 1f else 0.35f)
                    ),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = increaseDescription
                    }
                ) {
                    Text("＋", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                text = goalGuidance,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.primaryContainer.copy(alpha = 0.78f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = !isSaving,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.saving))
                }
            } else {
                Text(saveLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, name = "Habit form - Add")
@Preview(showBackground = true, name = "Habit form - Add Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Habit form - Add 320dp 2x", widthDp = 320, fontScale = 2f)
@Composable
private fun HabitFormAddPreview() {
    HabitFormPreview(name = "", targetPerWeek = 7, nameError = null, isSaving = false, showSuggestions = true)
}

@Preview(showBackground = true, name = "Habit form - Error")
@Composable
private fun HabitFormErrorPreview() {
    HabitFormPreview(name = "", targetPerWeek = 3, nameError = "请输入习惯名称", isSaving = false, showSuggestions = true)
}

@Preview(showBackground = true, name = "Habit form - Saving")
@Composable
private fun HabitFormSavingPreview() {
    HabitFormPreview(name = "阅读 30 分钟", targetPerWeek = 5, nameError = null, isSaving = true, showSuggestions = false)
}

@Composable
private fun HabitFormPreview(
    name: String,
    targetPerWeek: Int,
    nameError: String?,
    isSaving: Boolean,
    showSuggestions: Boolean
) {
    HabitTrackerTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            HabitFormContent(
                name = name,
                targetPerWeek = targetPerWeek,
                nameError = nameError,
                isSaving = isSaving,
                onNameChanged = {},
                onDecreaseTarget = {},
                onIncreaseTarget = {},
                onSave = {},
                saveLabel = stringResource(R.string.save_habit),
                showNameSuggestions = showSuggestions
            )
        }
    }
}
