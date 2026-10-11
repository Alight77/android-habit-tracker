package io.github.alight77.habittracker.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.domain.usecase.RecentGoalProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEditClick: (Int) -> Unit
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
        onStatsClick = onStatsClick,
        onEditClick = onEditClick,
        onDeleteHabit = viewModel::deleteHabit,
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScaffold(
    uiState: DashboardUiState,
    snackbarHostState: SnackbarHostState?,
    onCheckClick: (Int) -> Unit,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onRetry: () -> Unit
) {
    val addHabitContentDescription = stringResource(R.string.add_habit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onStatsClick) {
                        Text(stringResource(R.string.statistics))
                    }
                }
            )
        },
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(snackbarHostState)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.semantics {
                    contentDescription = addHabitContentDescription
                },
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onCheckClick: (Int) -> Unit,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onRetry: () -> Unit
) {
    DashboardScaffold(
        uiState = uiState,
        snackbarHostState = null,
        onCheckClick = onCheckClick,
        onAddClick = onAddClick,
        onStatsClick = onStatsClick,
        onEditClick = onEditClick,
        onDeleteHabit = onDeleteHabit,
        onRetry = onRetry
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
        DashboardUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading))
            }
        }

        is DashboardUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.message)
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        is DashboardUiState.Success -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                var headerHeightPx by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                // 空状态在页头与底部操作区之间居中；测量实际页头高度以适应字号和窄屏。
                val emptyMinHeight = (
                    maxHeight - with(density) { headerHeightPx.toDp() } - 88.dp
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
        Text(
            text = stringResource(R.string.start_with_habit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
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
                goalProgress = RecentGoalProgress(4, 5)
            ),
            HabitItemUiState(
                id = 2,
                name = "阅读",
                targetPerWeek = 7,
                isDoneToday = false,
                streak = 0,
                goalProgress = RecentGoalProgress(2, 7)
            )
        ),
        today = LocalDate.of(2026, 10, 11)
    )

    DashboardContent(
        uiState = previewState,
        onCheckClick = { _ -> },
        onAddClick = {},
        onStatsClick = {},
        onEditClick = {},
        onDeleteHabit = {},
        onRetry = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Empty")
@Composable
fun DashboardContentEmptyPreview() {
    DashboardContent(
        uiState = DashboardUiState.Success(emptyList(), LocalDate.of(2026, 10, 11)),
        onCheckClick = { _ -> },
        onAddClick = {},
        onStatsClick = {},
        onEditClick = {},
        onDeleteHabit = {},
        onRetry = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Loading")
@Composable
fun DashboardContentLoadingPreview() {
    DashboardContent(
        uiState = DashboardUiState.Loading,
        onCheckClick = { _ -> },
        onAddClick = {},
        onStatsClick = {},
        onEditClick = {},
        onDeleteHabit = {},
        onRetry = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Error")
@Composable
fun DashboardContentErrorPreview() {
    DashboardContent(
        uiState = DashboardUiState.Error("Failed to load habits"),
        onCheckClick = { _ -> },
        onAddClick = {},
        onStatsClick = {},
        onEditClick = {},
        onDeleteHabit = {},
        onRetry = {}
    )
}
