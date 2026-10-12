package io.github.alight77.habittracker.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.model.WeeklyTargetConstraints.MAX_TARGET_PER_WEEK
import io.github.alight77.habittracker.domain.model.WeeklyTargetConstraints.MIN_TARGET_PER_WEEK
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme

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
    saveLabel: String
) {
    val colors = MaterialTheme.colorScheme
    val decreaseTargetContentDescription = stringResource(R.string.decrease_weekly_goal)
    val increaseTargetContentDescription = stringResource(R.string.increase_weekly_goal)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth().testTag("habit_name_input"),
                    label = { Text(stringResource(R.string.habit_name)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(14.dp),
                    // 写入使用点击保存时的快照；处理中锁定字段，避免随后输入被误认为已保存。
                    enabled = !isSaving,
                    isError = nameError != null,
                    supportingText = nameError?.let { message ->
                        { Text(message) }
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            enabled = !isSaving && targetPerWeek > MIN_TARGET_PER_WEEK,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = decreaseTargetContentDescription
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
                            enabled = !isSaving && targetPerWeek < MAX_TARGET_PER_WEEK,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = increaseTargetContentDescription
                            }
                        ) {
                            Text("＋", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
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
    HabitFormPreview(name = "", targetPerWeek = 7, nameError = null, isSaving = false)
}

@Preview(showBackground = true, name = "Habit form - Error")
@Composable
private fun HabitFormErrorPreview() {
    HabitFormPreview(name = "", targetPerWeek = 3, nameError = "请输入习惯名称", isSaving = false)
}

@Preview(showBackground = true, name = "Habit form - Saving")
@Composable
private fun HabitFormSavingPreview() {
    HabitFormPreview(name = "每天阅读", targetPerWeek = 5, nameError = null, isSaving = true)
}

@Composable
private fun HabitFormPreview(name: String, targetPerWeek: Int, nameError: String?, isSaving: Boolean) {
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
                saveLabel = stringResource(R.string.save)
            )
        }
    }
}
