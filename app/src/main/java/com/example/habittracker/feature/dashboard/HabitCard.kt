package com.example.habittracker.feature.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habittracker.R
import com.example.habittracker.domain.usecase.RecentGoalProgress
import com.example.habittracker.ui.theme.HabitTrackerTheme

@Composable
fun HabitCard(
    habit: HabitItemUiState,
    onCheckClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val checkActionText = stringResource(
        if (habit.isDoneToday) R.string.completed else R.string.check_in
    )
    val checkStateDescription = stringResource(
        if (habit.isDoneToday) {
            R.string.habit_check_completed_state
        } else {
            R.string.habit_check_incomplete_state
        }
    )
    val checkContentDescription = stringResource(
        R.string.habit_check_content_description,
        checkActionText,
        habit.name
    )
    val moreActionsContentDescription = stringResource(R.string.habit_more_actions, habit.name)
    val cardContainerColor by animateColorAsState(
        targetValue = if (habit.isDoneToday) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "habit-card-container"
    )
    val buttonContainerColor by animateColorAsState(
        targetValue = if (habit.isDoneToday) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "habit-check-button"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 12.dp, end = 12.dp, bottom = 6.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.habit_target_summary, habit.targetPerWeek),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " · ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = stringResource(R.string.habit_streak_summary, habit.streak),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.habit_goal_progress,
                        habit.goalProgress.completed,
                        habit.goalProgress.target
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box {
                    IconButton(
                        modifier = Modifier.semantics {
                            contentDescription = moreActionsContentDescription
                        },
                        onClick = { moreMenuExpanded = true }
                    ) {
                        Text("⋮")
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                moreMenuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                moreMenuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
                Button(
                    modifier = Modifier
                        .widthIn(min = 72.dp)
                        .semantics {
                            contentDescription = checkContentDescription
                            stateDescription = checkStateDescription
                        },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainerColor,
                        contentColor = if (habit.isDoneToday) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = onCheckClick
                ) {
                    if (habit.isDoneToday) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✓")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = checkActionText, fontSize = 12.sp)
                        }
                    } else {
                        Text(text = checkActionText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HabitCardPreview() {
    HabitTrackerTheme {
        HabitCard(
            habit = HabitItemUiState(
                id = 1,
                name = "晨练",
                targetPerWeek = 7,
                isDoneToday = true,
                streak = 1,
                goalProgress = RecentGoalProgress(4, 7)
            ),
            onCheckClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 320, name = "HabitCard - Long name")
@Composable
fun HabitCardLongNamePreview() {
    HabitTrackerTheme {
        HabitCard(
            habit = HabitItemUiState(
                id = 1,
                name = "通勤时阅读 Android 工程实践文章",
                targetPerWeek = 7,
                isDoneToday = false,
                streak = 12,
                goalProgress = RecentGoalProgress(3, 7)
            ),
            onCheckClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}
