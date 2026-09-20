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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.habittracker.R
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
                onClick = onAddClick
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
            title = { Text(stringResource(R.string.delete_habit)) },
            text = { Text(stringResource(R.string.delete_habit_confirmation, habit.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        habitPendingDeletion = null
                        onDeleteHabit(habit.id)
                    }
                ) {
                    Text(stringResource(R.string.confirm))
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
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.start_with_habit),
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
                name = "晨练",
                targetPerWeek = 5,
                isDoneToday = true,
                streak = 4
            ),
            HabitItemUiState(
                id = 2,
                name = "阅读",
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
