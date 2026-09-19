package com.example.habittracker.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.habittracker.ui.component.HabitCard

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
        onCheckClick = { habitId, targetChecked ->
            viewModel.onHabitChecked(habitId, targetChecked)
        },
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
    onCheckClick: (Int, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDeleteHabit: (Int) -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HabitTracker") },
                actions = {
                    TextButton(onClick = onStatsClick) {
                        Text("统计")
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
            FloatingActionButton(onClick = onAddClick) {
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
    onCheckClick: (Int, Boolean) -> Unit,
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
    onCheckClick: (Int, Boolean) -> Unit,
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
                Text("加载中...")
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
                        Text("重试")
                    }
                }
            }
        }

        is DashboardUiState.Success -> {
            if (uiState.items.isEmpty()) {
                EmptyDashboard(modifier = modifier)
            } else {
                LazyColumn(modifier = modifier) {
                    items(
                        items = uiState.items,
                        key = { it.id }
                    ) { item ->
                        HabitCard(
                            habit = item,
                            onCheckClick = { onCheckClick(item.id, !item.isDoneToday) },
                            onEditClick = { onEditClick(item.id) },
                            onDeleteClick = { habitPendingDeletion = item }
                        )
                    }
                }
            }
        }
    }

    habitPendingDeletion?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitPendingDeletion = null },
            title = { Text("删除习惯") },
            text = { Text("确定删除“${habit.name}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        habitPendingDeletion = null
                        onDeleteHabit(habit.id)
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { habitPendingDeletion = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "从一个小习惯开始",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Preview(showBackground = true, name = "Dashboard - Success")
@Composable
fun DashboardContentSuccessPreview() {
    val previewState = DashboardUiState.Success(
        items = listOf(
            HabitItemUiState(
                id = 1,
                name = "Exercise",
                targetPerWeek = 5,
                isDoneToday = true,
                streak = 4
            ),
            HabitItemUiState(
                id = 2,
                name = "Read",
                targetPerWeek = 7,
                isDoneToday = false,
                streak = 0
            )
        )
    )

    DashboardContent(
        uiState = previewState,
        onCheckClick = { _, _ -> },
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
        uiState = DashboardUiState.Success(emptyList()),
        onCheckClick = { _, _ -> },
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
        onCheckClick = { _, _ -> },
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
        onCheckClick = { _, _ -> },
        onAddClick = {},
        onStatsClick = {},
        onEditClick = {},
        onDeleteHabit = {},
        onRetry = {}
    )
}
