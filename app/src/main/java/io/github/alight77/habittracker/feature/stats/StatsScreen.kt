package io.github.alight77.habittracker.feature.stats

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import io.github.alight77.habittracker.ui.theme.WarmPaperMiddle
import io.github.alight77.habittracker.ui.theme.WarmPaperBottom
import io.github.alight77.habittracker.ui.theme.WarmPaperTop
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    bottomBar: @Composable () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    StatsContent(
        uiState = state,
        onRetry = viewModel::retry,
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    uiState: StatsUiState,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val backgroundBrush = if (isLight) {
        Brush.verticalGradient(
            0f to WarmPaperTop,
            0.50f to WarmPaperMiddle,
            0.74f to colors.background,
            1f to WarmPaperBottom
        )
    } else {
        Brush.verticalGradient(listOf(colors.background, colors.surface))
    }

    Box(Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.stats_page_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = bottomBar,
            contentWindowInsets = WindowInsets.systemBars
        ) { padding ->
            when (uiState) {
                StatsUiState.Loading -> {
                    LoadingStats(modifier = Modifier.padding(padding))
                }

                is StatsUiState.Error -> {
                    ErrorStats(
                        message = uiState.message,
                        onRetry = onRetry,
                        modifier = Modifier.padding(padding)
                    )
                }

                is StatsUiState.Success -> {
                    if (uiState.summary.totalHabits == 0) {
                        // 零习惯仍保留统计计算结果，但不把七个零值展示成已有打卡足迹。
                        EmptyStats(modifier = Modifier.padding(padding))
                    } else {
                        StatsSuccessContent(
                            summary = uiState.summary,
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatsContentPreview() {
    HabitTrackerTheme {
        StatsContent(
            uiState = StatsUiState.Success(
                summary = StatsSummary(
                    totalHabits = 3,
                    todayDoneCount = 2,
                    totalDoneCount = 18,
                    recentSevenDayGoalPercent = 71,
                    longestStreak = 5,
                    recentSevenDayCounts = listOf(1, 3, 2, 0, 2, 3, 2).mapIndexed { index, count ->
                        DailyCheckInCount(LocalDate.of(2026, 10, 5).plusDays(index.toLong()), count)
                    }
                )
            ),
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Stats - Empty")
@Preview(showBackground = true, name = "Stats - Empty Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Stats - Empty 320dp 2x", widthDp = 320, fontScale = 2f)
@Composable
fun StatsContentEmptyPreview() {
    HabitTrackerTheme {
        StatsContent(
            uiState = StatsUiState.Success(
                StatsSummary(0, 0, 0, 0, 0, emptyList())
            ),
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Stats - Loading")
@Preview(showBackground = true, name = "Stats - Loading Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun StatsContentLoadingPreview() {
    HabitTrackerTheme {
        StatsContent(uiState = StatsUiState.Loading, onRetry = {})
    }
}

@Preview(showBackground = true, name = "Stats - Error")
@Preview(showBackground = true, name = "Stats - Error Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Stats - Error 320dp 2x", widthDp = 320, fontScale = 2f)
@Composable
fun StatsContentErrorPreview() {
    HabitTrackerTheme {
        StatsContent(
            uiState = StatsUiState.Error("加载统计数据失败，请重试"),
            onRetry = {}
        )
    }
}
