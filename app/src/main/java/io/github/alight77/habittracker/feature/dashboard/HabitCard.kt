package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.ui.icon.HabitIconCatalog
import io.github.alight77.habittracker.ui.icon.HabitIconPalette
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val chineseDayDateFormatter = DateTimeFormatter.ofPattern("uuuu年M月d日", Locale.SIMPLIFIED_CHINESE)
private val englishDayDateFormatter = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)
private val chineseDayWeekdayFormatter = DateTimeFormatter.ofPattern("E", Locale.SIMPLIFIED_CHINESE)
private val englishDayWeekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

@Composable
fun HabitCard(
    habit: HabitItemUiState,
    today: LocalDate,
    onCheckClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val icon = HabitIconCatalog.resolveForDisplay(habit.iconKey).definition
    val palette = icon.palette(colors.background.luminance() <= 0.5f)
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
            modifier = Modifier
                .fillMaxWidth()
                // 类别色只形成局部浅色层；正文仍使用主题的纸张色与文字色。
                .background(
                    Brush.verticalGradient(
                        listOf(palette.cardWash.compositeOver(colors.surface), colors.surface)
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(palette.iconContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(icon.drawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("habit_card_icon_${icon.key.value}")
                    )
                }
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
                        today = today,
                        palette = palette,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HabitCheckButton(
                        habit = habit,
                        palette = palette,
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
                        today = today,
                        palette = palette,
                        modifier = Modifier.weight(1f)
                    )
                    HabitCheckButton(habit = habit, palette = palette, onClick = onCheckClick)
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
    today: LocalDate,
    palette: HabitIconPalette,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val context = LocalContext.current
    val isChinese = stringResource(R.string.ui_language_tag) == "zh"
    val weekdayLabels = context.resources.getStringArray(R.array.habit_weekday_short)
    val dateFormatter = if (isChinese) chineseDayDateFormatter else englishDayDateFormatter
    val weekdayFormatter = if (isChinese) chineseDayWeekdayFormatter else englishDayWeekdayFormatter
    val checkedText = stringResource(R.string.habit_day_checked)
    val uncheckedText = stringResource(R.string.habit_day_unchecked)
    val beforeCreationText = stringResource(R.string.habit_day_before_creation)
    val todayUncheckedText = stringResource(R.string.habit_day_today_unchecked)
    val details = days.joinToString(if (isChinese) "，" else "; ") { day ->
        val isToday = day.date == today
        val state = when (day.status) {
            RecentDayStatus.CHECKED -> checkedText
            RecentDayStatus.BEFORE_CREATION -> beforeCreationText
            RecentDayStatus.NOT_CHECKED -> {
                if (isToday) todayUncheckedText else uncheckedText
            }
        }
        // 未打卡的今日状态已含“今天”；其他今日状态在模板中补充今天标记。
        val template = if (isToday && day.status != RecentDayStatus.NOT_CHECKED) {
            R.string.habit_recent_day_today_accessibility
        } else {
            R.string.habit_recent_day_accessibility
        }
        context.getString(template, dateFormatter.format(day.date), weekdayFormatter.format(day.date), state)
    }
    val description = stringResource(R.string.habit_recent_days_description, details)
    val squareSize = if (largeText) 22.dp else 14.dp

    // 七格依照传入日期逐日展示；今天由 Success.today 判定，不依赖列表位置或打卡状态。
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val isToday = day.date == today
            val shape = RoundedCornerShape(3.dp)
            val fill = when (day.status) {
                RecentDayStatus.CHECKED -> palette.checkedDay
                RecentDayStatus.NOT_CHECKED -> Color.Transparent
                RecentDayStatus.BEFORE_CREATION -> colors.surfaceVariant.copy(alpha = 0.45f)
            }
            val outline = when (day.status) {
                RecentDayStatus.CHECKED -> palette.checkedDay
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
                    text = if (isChinese && isToday) {
                        stringResource(R.string.habit_today_short)
                    } else {
                        weekdayLabels[day.date.dayOfWeek.value - 1]
                    },
                    color = if (!isChinese && isToday) colors.primary else colors.onSurfaceVariant,
                    fontSize = if (largeText) 11.sp else 10.sp,
                    fontWeight = if (!isChinese && isToday) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                if (!isChinese && isToday) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .background(colors.primary, CircleShape)
                            .testTag("recent-check-in-today-marker")
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitCheckButton(
    habit: HabitItemUiState,
    palette: HabitIconPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.5f
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
            containerColor = if (done) palette.completedContainer else colors.surface,
            contentColor = if (done) palette.onCompletedContainer else palette.uncheckedAction
        ),
        border = if (done) null else BorderStroke(1.5.dp, palette.uncheckedAction),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (done) {
            Box(
                modifier = Modifier
                    // 对勾会随系统字号放大，承载圆形也须放大以免在 2 倍字体下被裁切。
                    .size(if (largeText) 32.dp else 22.dp)
                    .background(palette.onCompletedContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = palette.completedContainer,
                    fontSize = if (largeText) 11.sp else 14.sp,
                    lineHeight = if (largeText) 11.sp else 14.sp,
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
            iconKey = IconKey.READING,
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
            iconKey = IconKey.EXERCISE,
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
                        iconKey = IconKey.EXERCISE,
                        targetPerWeek = 5,
                        isDoneToday = true,
                        streak = 4,
                        recentDays = previewRecentDays(setOf(0L, 1L, 2L, 3L))
                    ),
                    today = LocalDate.of(2026, 10, 11),
                    onCheckClick = {},
                    onEditClick = {},
                    onDeleteClick = {}
                )
                HabitCard(
                    habit = HabitItemUiState(
                        id = 2,
                        name = "阅读",
                        iconKey = IconKey.READING,
                        targetPerWeek = 7,
                        isDoneToday = false,
                        streak = 0,
                        recentDays = previewRecentDays(setOf(2L, 4L))
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

@Preview(showBackground = true, widthDp = 320, fontScale = 2f, name = "HabitCard - Long name 2x")
@Composable
private fun HabitCardLongNamePreview() {
    HabitCardPreviewContent(
        HabitItemUiState(
            id = 1,
            name = "通勤时阅读 Android 工程实践文章",
            iconKey = IconKey.READING,
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
            iconKey = IconKey.EXERCISE,
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
                today = LocalDate.of(2026, 10, 11),
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
