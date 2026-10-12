package io.github.alight77.habittracker.feature.stats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import io.github.alight77.habittracker.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun StatsSuccessContent(summary: StatsSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 32.dp)
    ) {
        TodayOverview(summary)
        SectionDivider()
        RecentCheckInChart(summary)
        SectionDivider()
        GoalRateSection(summary)
        SectionDivider()
        HistoryMetrics(summary)
    }
}

@Composable
private fun TodayOverview(summary: StatsSummary) {
    val colors = MaterialTheme.colorScheme
    val hasWideCount = summary.todayDoneCount >= 10 || summary.totalHabits >= 10
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val showArtwork = maxWidth >= 320.dp &&
            !hasWideCount &&
            LocalDensity.current.fontScale < 1.5f &&
            colors.background.luminance() > 0.5f
        val availableWidth = with(LocalDensity.current) {
            (maxWidth * if (showArtwork) 0.51f else 1f).roundToPx()
        }
        val doneText = summary.todayDoneCount.toString()
        val totalText = summary.totalHabits.toString()
        // 实测整组数字宽度后再选字号，避免窄屏和放大字体时分数越过页面边界。
        val countSize = listOf(
            if (hasWideCount) 56.sp else 68.sp, 48.sp, 40.sp, 32.sp, 24.sp, 20.sp
        ).firstOrNull { size ->
            val countStyle = TextStyle(fontSize = size, fontWeight = FontWeight.Bold)
            val slashStyle = TextStyle(fontSize = minOf(42f, size.value * 0.75f).sp)
            textMeasurer.measure(doneText, style = countStyle).size.width +
                textMeasurer.measure(" / ", style = slashStyle).size.width +
                textMeasurer.measure(totalText, style = countStyle).size.width <= availableWidth
        }

        Box(Modifier.fillMaxWidth().heightIn(min = if (showArtwork) 238.dp else 205.dp)) {
            if (showArtwork) {
                Image(
                    painter = painterResource(R.drawable.stats_journal_illustration),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(182.dp)
                )
            }
            Column(Modifier.fillMaxWidth(if (showArtwork) 0.51f else 1f)) {
                SectionTitle(stringResource(R.string.stats_today_persistence))
                Spacer(Modifier.height(if (showArtwork) 36.dp else 20.dp))
                if (countSize == null) {
                    // 极端长数字保留完整分子和分母，允许总览自然增高。
                    Text(doneText, color = colors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("/ $totalText", color = colors.onBackground, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doneText,
                        color = colors.primary,
                        fontSize = countSize,
                        lineHeight = 76.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = " / ",
                        color = colors.outline,
                        fontSize = minOf(42f, countSize.value * 0.75f).sp,
                        lineHeight = 62.sp,
                        maxLines = 1
                    )
                    Text(
                        text = totalText,
                        color = colors.onBackground,
                        fontSize = countSize,
                        lineHeight = 76.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.stats_hero_motto),
                    color = colors.onSurfaceVariant,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

@Composable
private fun RecentCheckInChart(summary: StatsSummary) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 82.dp)) {
        if (colors.background.luminance() > 0.5f && LocalDensity.current.fontScale < 1.5f) {
            Image(
                painter = painterResource(R.drawable.stats_leaf_shadow),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).width(162.dp).height(80.dp).alpha(0.38f)
            )
        }
        Column {
            SectionTitle(stringResource(R.string.stats_recent_checkin_footprint))
            Text(
                text = stringResource(R.string.stats_daily_completed_habits),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    val days = summary.recentSevenDayCounts
    // 柱高只表达每日打卡数；目标达成率使用独立的统计口径。
    // 只用当前七日峰值定标；习惯总数较多但打卡较少时，非零柱仍能看出分布。
    val maxCount = maxOf(days.maxOfOrNull { it.completedHabits } ?: 0, 1)
    // 全零时缩短图表留白，避免空数据把历史指标挤出首屏。
    val chartHeight = if (days.all { it.completedHabits == 0 }) 64.dp else 90.dp
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.SIMPLIFIED_CHINESE)
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (largeText) Modifier.horizontalScroll(rememberScrollState()) else Modifier
        )
    ) {
        days.forEachIndexed { index, day ->
            val label = if (index == days.lastIndex) {
                stringResource(R.string.stats_today_short)
            } else {
                day.date.format(weekdayFormatter)
            }
            val description = stringResource(
                R.string.stats_daily_checkin_description,
                label,
                day.completedHabits
            )
            Column(
                modifier = Modifier
                    .then(if (largeText) Modifier.width(58.dp) else Modifier.weight(1f))
                    .clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.height(chartHeight),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.completedHabits.toString(),
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(5.dp))
                    if (day.completedHabits == 0) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(colors.outlineVariant, CircleShape)
                        )
                    } else {
                        val barHeight = (58.dp * (day.completedHabits.toFloat() / maxCount))
                            .coerceAtLeast(4.dp)
                        Box(
                            Modifier
                                .width(22.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(colors.primary)
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    text = label,
                    color = if (index == days.lastIndex) colors.primary else colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (index == days.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GoalRateSection(summary: StatsSummary) {
    val colors = MaterialTheme.colorScheme
    val percent = summary.recentSevenDayGoalPercent.coerceIn(0, 100)
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val progressDescription = stringResource(R.string.stats_goal_rate_accessibility, percent)
    SectionTitle(
        stringResource(
            if (largeText) R.string.stats_recent_seven_day_goal_rate_compact
            else R.string.stats_recent_seven_day_goal_rate
        )
    )
    if (largeText) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
            Text(
                text = "$percent%",
                color = colors.primary,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.stats_effective_goal_note),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$percent%",
                color = colors.primary,
                fontSize = 52.sp,
                lineHeight = 60.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.stats_effective_goal_note),
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(13.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceVariant)
            .semantics {
                // 进度条单独获得焦点时，明确它表示目标达成率，而非七日打卡次数。
                contentDescription = progressDescription
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent / 100f)
                .height(13.dp)
                .background(colors.primary)
        )
    }
}

@Composable
private fun HistoryMetrics(summary: StatsSummary) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryMetric(
                    label = stringResource(R.string.stats_existing_habits_total_checkins),
                    value = summary.totalDoneCount,
                    unit = stringResource(R.string.stats_times),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colors.outlineVariant)
                HistoryMetric(
                    label = stringResource(R.string.stats_single_habit_historical_longest),
                    value = summary.longestStreak,
                    unit = stringResource(R.string.stats_days),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HistoryMetric(
                    label = stringResource(R.string.stats_existing_habits_total_checkins),
                    value = summary.totalDoneCount,
                    unit = stringResource(R.string.stats_times),
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.width(1.dp).height(88.dp).background(colors.outlineVariant))
                HistoryMetric(
                    label = stringResource(R.string.stats_single_habit_historical_longest),
                    value = summary.longestStreak,
                    unit = stringResource(R.string.stats_days),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: Int, unit: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    Column(modifier = modifier) {
        Text(
            text = label,
            color = colors.onBackground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = with(LocalDensity.current) { maxWidth.roundToPx() }
            val valueSize = listOf(44.sp, 38.sp, 32.sp, 26.sp, 20.sp)
                .firstOrNull { size ->
                    textMeasurer.measure(
                        value.toString(), style = TextStyle(fontSize = size, fontWeight = FontWeight.Bold)
                    ).size.width + textMeasurer.measure(
                        unit, style = TextStyle(fontSize = 18.sp)
                    ).size.width + with(LocalDensity.current) { 5.dp.roundToPx() } <= availableWidth
                }
            if (valueSize == null) {
                Text(value.toString(), color = colors.primary, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Text(unit, color = colors.onSurfaceVariant, fontSize = 18.sp)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value.toString(),
                        color = colors.primary,
                        fontSize = valueSize,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = unit,
                        color = colors.onSurfaceVariant,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 5.dp, bottom = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp
    )
}
