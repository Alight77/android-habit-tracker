package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import java.time.LocalDate

@Composable
fun HabitCard(
    habit: HabitItemUiState,
    onCheckClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val moreActionsContentDescription = stringResource(R.string.habit_more_actions, habit.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = habit.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 3.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (largeText) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(
                        modifier = Modifier.semantics {
                            contentDescription = moreActionsContentDescription
                        },
                        onClick = { moreMenuExpanded = true }
                    ) {
                        Text(
                            text = "⋮",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurfaceVariant
                        )
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
            }

            val targetText = stringResource(R.string.habit_target_summary, habit.targetPerWeek)
            val streakText = stringResource(R.string.habit_streak_summary, habit.streak)
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardMetaText(targetText)
                    CardMetaText(streakText)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardMetaText(targetText)
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    CardMetaText(streakText)
                }
            }

            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecentCheckInStrip(
                        days = habit.recentDays,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HabitCheckButton(
                        habit = habit,
                        onClick = onCheckClick,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecentCheckInStrip(
                        days = habit.recentDays,
                        modifier = Modifier.weight(1f)
                    )
                    HabitCheckButton(habit = habit, onClick = onCheckClick)
                }
            }
        }
    }
}

@Composable
private fun CardMetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RecentCheckInStrip(
    days: List<RecentCheckInDay>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val checkedText = stringResource(R.string.habit_day_checked)
    val uncheckedText = stringResource(R.string.habit_day_unchecked)
    val beforeCreationText = stringResource(R.string.habit_day_before_creation)
    val todayUncheckedText = stringResource(R.string.habit_day_today_unchecked)
    val details = days.joinToString("，") { day ->
        val state = when (day.status) {
            RecentDayStatus.CHECKED -> checkedText
            RecentDayStatus.BEFORE_CREATION -> beforeCreationText
            RecentDayStatus.NOT_CHECKED -> {
                if (day == days.last()) todayUncheckedText else uncheckedText
            }
        }
        "${day.date.monthValue}月${day.date.dayOfMonth}日$state"
    }
    val description = stringResource(R.string.habit_recent_days_description, details)
    val squareSize = if (largeText) 22.dp else 14.dp

    // 七格保留逐日历史；目标和连续天数已在上一行展示，这里只用“今”标明时间方向。
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val shape = RoundedCornerShape(3.dp)
            val fill = when (day.status) {
                RecentDayStatus.CHECKED -> colors.primary
                RecentDayStatus.NOT_CHECKED -> Color.Transparent
                RecentDayStatus.BEFORE_CREATION -> colors.surfaceVariant.copy(alpha = 0.45f)
            }
            val outline = when (day.status) {
                RecentDayStatus.CHECKED -> colors.primary
                RecentDayStatus.NOT_CHECKED -> colors.outline
                RecentDayStatus.BEFORE_CREATION -> colors.outlineVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(squareSize)
                        .testTag("recent-check-in-day")
                        .background(fill, shape)
                        .border(1.dp, outline, shape)
                )
                Text(
                    text = if (day == days.last()) {
                        stringResource(R.string.habit_today_short)
                    } else {
                        "一二三四五六日"[day.date.dayOfWeek.value - 1].toString()
                    },
                    color = colors.onSurfaceVariant,
                    fontSize = if (largeText) 11.sp else 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HabitCheckButton(
    habit: HabitItemUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val done = habit.isDoneToday
    val actionText = stringResource(if (done) R.string.completed else R.string.check_in)
    val checkStateDescription = stringResource(
        if (done) R.string.habit_check_completed_state else R.string.habit_check_incomplete_state
    )
    val checkContentDescription = stringResource(
        R.string.habit_check_content_description,
        actionText,
        habit.name
    )

    Button(
        modifier = modifier
            .widthIn(min = 104.dp)
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = checkContentDescription
                stateDescription = checkStateDescription
            },
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (done) colors.tertiary else colors.surface,
            contentColor = if (done) colors.onTertiary else colors.primary
        ),
        border = if (done) null else BorderStroke(1.5.dp, colors.primary),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(colors.onTertiary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = colors.tertiary,
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = actionText, maxLines = 1)
    }
}

@Preview(showBackground = true, widthDp = 360, name = "HabitCard - Unchecked")
@Composable
private fun HabitCardUncheckedPreview() {
    HabitCardPreviewContent(
        HabitItemUiState(
            id = 1,
            name = "阅读",
            targetPerWeek = 7,
            isDoneToday = false,
            streak = 0,
            recentDays = previewRecentDays(setOf(2L, 4L))
        )
    )
}

@Preview(showBackground = true, widthDp = 360, name = "HabitCard - Completed")
@Composable
private fun HabitCardCompletedPreview() {
    HabitCardPreviewContent(
        HabitItemUiState(
            id = 1,
            name = "晨练",
            targetPerWeek = 5,
            isDoneToday = true,
            streak = 4,
            recentDays = previewRecentDays(setOf(0L, 1L, 2L, 3L))
        )
    )
}

@Preview(showBackground = true, widthDp = 360, name = "HabitCard - Multiple")
@Composable
private fun HabitCardMultiplePreview() {
    HabitTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                HabitCard(
                    habit = HabitItemUiState(
                        id = 1,
                        name = "晨练",
                        targetPerWeek = 5,
                        isDoneToday = true,
                        streak = 4,
                        recentDays = previewRecentDays(setOf(0L, 1L, 2L, 3L))
                    ),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
                HabitCard(
                    habit = HabitItemUiState(
                        id = 2,
                        name = "阅读",
                        targetPerWeek = 7,
                        isDoneToday = false,
                        streak = 0,
                        recentDays = previewRecentDays(setOf(2L, 4L))
                    ),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 2f, name = "HabitCard - Long name 2x")
@Composable
private fun HabitCardLongNamePreview() {
    HabitCardPreviewContent(
        HabitItemUiState(
            id = 1,
            name = "通勤时阅读 Android 工程实践文章",
            targetPerWeek = 7,
            isDoneToday = false,
            streak = 12345,
            recentDays = previewRecentDays(emptySet(), createdDaysAgo = 0)
        )
    )
}

@Preview(
    showBackground = true,
    widthDp = 360,
    name = "HabitCard - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HabitCardDarkPreview() {
    HabitCardPreviewContent(
        HabitItemUiState(
            id = 1,
            name = "晨练",
            targetPerWeek = 5,
            isDoneToday = true,
            streak = 4,
            recentDays = previewRecentDays(setOf(0L, 1L, 2L, 3L))
        ),
        darkTheme = true
    )
}

@Composable
private fun HabitCardPreviewContent(habit: HabitItemUiState, darkTheme: Boolean = false) {
    HabitTrackerTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            HabitCard(
                habit = habit,
                onCheckClick = {},
                onEditClick = {},
                onDeleteClick = {}
            )
        }
    }
}

private fun previewRecentDays(
    checkedDaysAgo: Set<Long>,
    createdDaysAgo: Long = 6
): List<RecentCheckInDay> {
    val today = LocalDate.of(2026, 10, 11)
    return recentCheckInDays(
        createdDate = today.minusDays(createdDaysAgo),
        doneDates = checkedDaysAgo.map(today::minusDays),
        today = today
    )
}
