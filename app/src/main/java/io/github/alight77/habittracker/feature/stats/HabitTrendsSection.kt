package io.github.alight77.habittracker.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import io.github.alight77.habittracker.R
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortWeekDateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.ROOT)
private val chineseWeekDateFormatter = DateTimeFormatter.ofPattern("uuuu年M月d日", Locale.SIMPLIFIED_CHINESE)
private val englishWeekDateFormatter = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)

@Composable
internal fun HabitTrendsSection(
    availableHabits: List<StatsHabitOption>,
    selectedHabitId: Int?,
    weeklyTrend: List<WeeklyCheckInTrendPoint>,
    onHabitSelected: (Int) -> Unit
) {
    val selectedHabit = availableHabits.firstOrNull { habit -> habit.id == selectedHabitId }
        ?: return
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.stats_habit_trends),
        color = colors.onBackground,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = stringResource(R.string.stats_weekly_checkin_days),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        val selectorDescription = stringResource(R.string.stats_trend_select_habit_description, selectedHabit.name)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = selectorDescription }
        ) {
            Text(
                text = selectedHabit.name,
                // 按钮由完整描述统一播报，省略显示的文字不再成为第二条语义。
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "▾",
                modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {},
                color = colors.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = menuWidth)
        ) {
            availableHabits.forEach { habit ->
                DropdownMenuItem(
                    text = { Text(habit.name) },
                    onClick = {
                        expanded = false
                        onHabitSelected(habit.id)
                    }
                )
            }
        }
    }

    if (weeklyTrend.size == 5) {
        Spacer(Modifier.height(16.dp))
        WeeklyTrendChart(weeklyTrend)
        Text(
            text = stringResource(R.string.stats_trend_this_week_description),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            // 当前周的数据点已完整播报该说明，避免 TalkBack 重复获得一个焦点。
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clearAndSetSemantics { testTag = "stats-trend-progress-note" }
        )
    }
}

@Composable
private fun WeeklyTrendChart(points: List<WeeklyCheckInTrendPoint>) {
    val isChinese = stringResource(R.string.ui_language_tag) == "zh"
    val dateFormatter = if (isChinese) chineseWeekDateFormatter else englishWeekDateFormatter
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        val scrollHorizontally = largeText || maxWidth < 250.dp
        val columnWidth = if (largeText) 96.dp else 76.dp
        // 大字体和窄屏时固定每列触摸／阅读宽度，仅图表横向滚动；页面仍只有外层纵向滚动。
        Row(
            modifier = Modifier.fillMaxWidth().then(
                if (scrollHorizontally) Modifier.horizontalScroll(rememberScrollState()) else Modifier
            ).testTag("stats-trend-chart"),
            horizontalArrangement = Arrangement.spacedBy(if (scrollHorizontally) 4.dp else 0.dp)
        ) {
            points.forEachIndexed { index, point ->
                val columnModifier = if (scrollHorizontally) Modifier.width(columnWidth)
                else Modifier.weight(1f)
                WeeklyTrendColumn(
                    point = point,
                    index = index,
                    dateFormatter = dateFormatter,
                    modifier = columnModifier
                )
            }
        }
    }
}

@Composable
private fun WeeklyTrendColumn(
    point: WeeklyCheckInTrendPoint,
    index: Int,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val weekRange = if (point.notYetCreated) {
        stringResource(
            R.string.stats_trend_week_not_created_description,
            point.weekStart.format(dateFormatter), point.weekEnd.format(dateFormatter)
        )
    } else if (point.completedDays == 1) {
        stringResource(
            R.string.stats_trend_week_description_one,
            point.weekStart.format(dateFormatter), point.weekEnd.format(dateFormatter)
        )
    } else {
        stringResource(
            R.string.stats_trend_week_description,
            point.weekStart.format(dateFormatter), point.weekEnd.format(dateFormatter),
            point.completedDays
        )
    }
    val description = if (point.isCurrentWeek) {
        "$weekRange ${stringResource(R.string.stats_trend_this_week_description)}"
    } else weekRange
    val label = if (point.isCurrentWeek) stringResource(R.string.stats_trend_this_week)
    else point.weekStart.format(shortWeekDateFormatter)
    val maxBarHeight = 80.dp
    val plotHeight = maxBarHeight + 8.dp + with(LocalDensity.current) {
        MaterialTheme.typography.titleSmall.lineHeight.toDp()
    }

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            testTag = "stats-trend-week-$index"
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.height(plotHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            if (point.notYetCreated) {
                Text(
                    text = "—",
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                // 数值跟随柱顶或零值标记；各列图形保持同一底线。
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = point.completedDays.toString(),
                        color = colors.onBackground,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    if (point.completedDays == 0) {
                        Box(Modifier.size(10.dp).border(1.dp, colors.outline, CircleShape))
                    } else {
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(maxBarHeight * (point.completedDays.coerceIn(0, 7) / 7f))
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(colors.primary)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (point.isCurrentWeek) colors.primary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (point.isCurrentWeek) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
