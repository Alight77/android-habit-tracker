package io.github.alight77.habittracker.feature.stats

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import io.github.alight77.habittracker.ui.theme.WarmPaperMiddle
import io.github.alight77.habittracker.ui.theme.WarmPaperBottom
import io.github.alight77.habittracker.ui.theme.WarmPaperTop
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://alight77.github.io/android-habit-tracker/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    bottomBar: @Composable () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    StatsContent(
        uiState = state,
        onRetry = viewModel::retry,
        bottomBar = bottomBar,
        onOpenPrivacyPolicy = { uri ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    uiState: StatsUiState,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    onOpenPrivacyPolicy: (Uri) -> Boolean = { false }
) {
    val colors = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val privacyPolicyLabel = stringResource(R.string.privacy_policy)
    val openLinkFailedMessage = stringResource(R.string.privacy_policy_open_failed)
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.systemBars
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val viewportHeight = maxHeight
                // 状态内容与页脚共用滚动区域，避免链接常驻屏幕底部挤压统计指标。
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (uiState) {
                        StatsUiState.Loading -> LoadingStats(Modifier.heightIn(min = viewportHeight))

                        is StatsUiState.Error -> ErrorStats(
                            message = stringResource(uiState.messageResId),
                            onRetry = onRetry,
                            modifier = Modifier.heightIn(min = viewportHeight)
                        )

                        is StatsUiState.Success -> {
                            if (uiState.summary.totalHabits == 0) {
                                // 零习惯仍保留统计计算结果，但不把七个零值展示成已有打卡足迹。
                                EmptyStats(Modifier.heightIn(min = viewportHeight))
                            } else {
                                StatsSuccessContent(summary = uiState.summary)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = {
                                if (!onOpenPrivacyPolicy(Uri.parse(PRIVACY_POLICY_URL))) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(openLinkFailedMessage)
                                    }
                                }
                            },
                            modifier = Modifier
                                .widthIn(min = 48.dp)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colors.onSurfaceVariant
                            )
                        ) {
                            Text(privacyPolicyLabel)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Stats - Success")
@Preview(showBackground = true, name = "Stats - Success Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Stats - Success 320dp 2x", widthDp = 320, fontScale = 2f)
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
            uiState = StatsUiState.Error(R.string.stats_load_failed),
            onRetry = {}
        )
    }
}
