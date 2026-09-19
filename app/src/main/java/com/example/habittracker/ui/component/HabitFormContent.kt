package com.example.habittracker.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.habittracker.viewmodel.MAX_TARGET_PER_WEEK
import com.example.habittracker.viewmodel.MIN_TARGET_PER_WEEK

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
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text("习惯名称") },
            isError = nameError != null,
            supportingText = nameError?.let { message ->
                { Text(message) }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("每周目标次数")
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onDecreaseTarget,
                enabled = targetPerWeek > MIN_TARGET_PER_WEEK
            ) {
                Text("−")
            }

            Spacer(modifier = Modifier.width(16.dp))
            Text("$targetPerWeek 次 / 周")
            Spacer(modifier = Modifier.width(16.dp))

            OutlinedButton(
                onClick = onIncreaseTarget,
                enabled = targetPerWeek < MAX_TARGET_PER_WEEK
            ) {
                Text("＋")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSave,
            enabled = !isSaving
        ) {
            if (isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存中…")
                }
            } else {
                Text(saveLabel)
            }
        }
    }
}
