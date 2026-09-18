package com.example.habittracker.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habittracker.ui.component.HabitCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
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
        onStatsClick = onStatsClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScaffold(
    uiState: DashboardUiState,
    snackbarHostState: SnackbarHostState?,
    onCheckClick: (Int, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit
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
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onCheckClick: (Int, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    DashboardScaffold(
        uiState = uiState,
        snackbarHostState = null,
        onCheckClick = onCheckClick,
        onAddClick = onAddClick,
        onStatsClick = onStatsClick
    )
}

@Composable
private fun DashboardBody(
    uiState: DashboardUiState,
    onCheckClick: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(uiState.message)
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
                            onCheckClick = { onCheckClick(item.id, !item.isDoneToday) }
                        )
                    }
                }
            }
        }
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
                text = "还没有 habit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
        onStatsClick = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Empty")
@Composable
fun DashboardContentEmptyPreview() {
    DashboardContent(
        uiState = DashboardUiState.Success(emptyList()),
        onCheckClick = { _, _ -> },
        onAddClick = {},
        onStatsClick = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Loading")
@Composable
fun DashboardContentLoadingPreview() {
    DashboardContent(
        uiState = DashboardUiState.Loading,
        onCheckClick = { _, _ -> },
        onAddClick = {},
        onStatsClick = {}
    )
}

@Preview(showBackground = true, name = "Dashboard - Error")
@Composable
fun DashboardContentErrorPreview() {
    DashboardContent(
        uiState = DashboardUiState.Error("Failed to load habits"),
        onCheckClick = { _, _ -> },
        onAddClick = {},
        onStatsClick = {}
    )
}