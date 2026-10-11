package io.github.alight77.habittracker.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBackClick: () -> Unit
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    StatsContent(
        uiState = state,
        onBackClick = onBackClick,
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    uiState: StatsUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit
) {
    val backContentDescription = stringResource(R.string.back)
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
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.semantics {
                                contentDescription = backContentDescription
                            }
                        ) {
                            Text("←", fontSize = 28.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            contentWindowInsets = WindowInsets.systemBars
        ) { padding ->
            when (uiState) {
                StatsUiState.Loading -> {
                    Text(
                        text = "加载中...",
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                    )
                }

                is StatsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Text(uiState.message)
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                is StatsUiState.Success -> {
                    StatsSuccessContent(
                        summary = uiState.summary,
                        modifier = Modifier.padding(padding)
                    )
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
            onBackClick = {},
            onRetry = {}
        )
    }
}
