package io.github.alight77.habittracker.feature.dashboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme
import io.github.alight77.habittracker.ui.theme.WarmPaperMiddle
import io.github.alight77.habittracker.ui.theme.WarmPaperBottom
import io.github.alight77.habittracker.ui.theme.WarmPaperTop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is DashboardUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    DashboardScaffold(
        uiState = state,
        snackbarHostState = snackbarHostState,
        onCheckClick = viewModel::onHabitToggle,
        onAddClick = onAddClick,
        onEditClick = onEditClick,
        onDeleteHabit = viewModel::deleteHabit,
        onRetry = viewModel::retry,
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScaffold(
    uiState: DashboardUiState,
    snackbarHostState: SnackbarHostState?,
    onCheckClick: (Int) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val addHabitContentDescription = stringResource(R.string.add_habit)
    val colors = MaterialTheme.colorScheme
    val showArtwork = colors.background.luminance() > 0.5f && LocalDensity.current.fontScale < 1.5f
    val backgroundBrush = if (showArtwork) {
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
        if (showArtwork) {
            // 页头插画是轻量背景资源；日期、寄语和操作始终由 Compose 绘制。
            Image(
                painter = painterResource(R.drawable.dashboard_sunny_header),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        // 末端逐渐透明，让水彩插画自然落到 Compose 背景渐变上。
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White,
                                0.72f to Color.White,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .align(Alignment.TopCenter)
            )
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.dashboard_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (LocalDensity.current.fontScale >= 1.5f) 18.sp else 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.onBackground
                    )
                )
            },
            snackbarHost = {
                if (snackbarHostState != null) {
                    SnackbarHost(snackbarHostState)
                }
            },
            bottomBar = bottomBar,
            floatingActionButton = {
                FloatingActionButton(
                    modifier = Modifier.semantics {
                        contentDescription = addHabitContentDescription
                    },
                    onClick = onAddClick,
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ) {
                    Text("+")
                }
            },
            contentWindowInsets = WindowInsets.systemBars
        ) { padding ->
            DashboardBody(
                uiState = uiState,
                onCheckClick = onCheckClick,
                onEditClick = onEditClick,
                onDeleteHabit = onDeleteHabit,
                onRetry = onRetry,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onCheckClick: (Int) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    DashboardScaffold(
        uiState = uiState,
        snackbarHostState = null,
        onCheckClick = onCheckClick,
        onAddClick = onAddClick,
        onEditClick = onEditClick,
        onDeleteHabit = onDeleteHabit,
        onRetry = onRetry,
        bottomBar = bottomBar
    )
}

@Composable
private fun DashboardBody(
    uiState: DashboardUiState,
    onCheckClick: (Int) -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var habitPendingDeletion by remember { mutableStateOf<HabitItemUiState?>(null) }

    when (uiState) {
        DashboardUiState.Loading -> LoadingDashboard(modifier)

        is DashboardUiState.Error -> ErrorDashboard(uiState.message, onRetry, modifier)

        is DashboardUiState.Success -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                var headerHeightPx by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                // 空状态在页头与底部操作区之间居中；测量实际页头高度以适应字号和窄屏。
                val emptyMinHeight = (
                    this.maxHeight - with(density) { headerHeightPx.toDp() } - 88.dp
                ).coerceAtLeast(0.dp)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item(key = "today_header") {
                        Box(modifier = Modifier.onSizeChanged { headerHeightPx = it.height }) {
                            // 页头直接使用与卡片同一份 Success 状态中的日期，避免出现旧日标题。
                            TodayJournalHeader(today = uiState.today)
                        }
                    }
                    if (uiState.items.isEmpty()) {
                        item(key = "empty") {
                            EmptyDashboard(
                                modifier = Modifier
                                    .heightIn(min = emptyMinHeight)
                                    .testTag("dashboardEmptyArea")
                            )
                        }
                    } else {
                        items(
                            items = uiState.items,
                            key = { it.id }
                        ) { item ->
                            HabitCard(
                                habit = item,
                                // 卡片只上报点击；目标由 ViewModel 根据最新意图同步计算。
                                onCheckClick = { onCheckClick(item.id) },
                                onEditClick = { onEditClick(item.id) },
                                onDeleteClick = { habitPendingDeletion = item }
                            )
                        }
                    }
                }
            }
        }
    }

    habitPendingDeletion?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitPendingDeletion = null },
            title = { Text(stringResource(R.string.delete_habit)) },
            text = { Text(stringResource(R.string.delete_habit_confirmation, habit.name)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        habitPendingDeletion = null
                        onDeleteHabit(habit.id)
                    }
                ) {
                    Text(stringResource(R.string.confirm_permanent_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { habitPendingDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        DashboardStatusPanel(modifier = Modifier.testTag("dashboardEmptyContent")) {
            Text(
                text = stringResource(R.string.start_with_habit),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.dashboard_empty_guidance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingDashboard(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        DashboardStatusPanel {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp).clearAndSetSemantics { },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorDashboard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = this.maxHeight
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 短屏或大字号时允许滚动到重试按钮；空间足够时仍保持状态卡片居中。
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                DashboardStatusPanel {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatusPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Preview(showBackground = true, name = "Dashboard - Success")
@Composable
fun DashboardContentSuccessPreview() {
    val previewState = DashboardUiState.Success(
        items = listOf(
            HabitItemUiState(
                id = 1,
                name = "晨练",
                targetPerWeek = 5,
                isDoneToday = true,
                streak = 4,
                recentDays = recentCheckInDays(
                    LocalDate.of(2026, 10, 5),
                    (0L..3L).map(LocalDate.of(2026, 10, 11)::minusDays),
                    LocalDate.of(2026, 10, 11)
                )
            ),
            HabitItemUiState(
                id = 2,
                name = "阅读",
                targetPerWeek = 7,
                isDoneToday = false,
                streak = 0,
                recentDays = recentCheckInDays(
                    LocalDate.of(2026, 10, 5),
                    listOf(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 7)),
                    LocalDate.of(2026, 10, 11)
                )
            )
        ),
        today = LocalDate.of(2026, 10, 11)
    )

    DashboardContent(
        uiState = previewState,
        onCheckClick = { _ -> },
        onAddClick = {},
        onEditClick = {},
        onDeleteHabit = {},
        onRetry = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Empty")
@Preview(showBackground = true, name = "Dashboard - Empty Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Dashboard - Empty 320dp 2x", widthDp = 320, fontScale = 2f)
@Composable
fun DashboardContentEmptyPreview() {
    HabitTrackerTheme {
        DashboardContent(
            uiState = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 11)),
            onCheckClick = { _ -> },
            onAddClick = {},
            onEditClick = {},
            onDeleteHabit = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Dashboard - Loading")
@Preview(showBackground = true, name = "Dashboard - Loading Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DashboardContentLoadingPreview() {
    HabitTrackerTheme {
        DashboardContent(
            uiState = DashboardUiState.Loading,
            onCheckClick = { _ -> },
            onAddClick = {},
            onEditClick = {},
            onDeleteHabit = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Dashboard - Error")
@Preview(showBackground = true, name = "Dashboard - Error Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Dashboard - Error 320dp 2x", widthDp = 320, fontScale = 2f)
@Composable
fun DashboardContentErrorPreview() {
    HabitTrackerTheme {
        DashboardContent(
            uiState = DashboardUiState.Error("加载习惯数据失败，请重试"),
            onCheckClick = { _ -> },
            onAddClick = {},
            onEditClick = {},
            onDeleteHabit = {},
            onRetry = {}
        )
    }
}
