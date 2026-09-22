package io.github.alight77.habittracker.feature.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.component.HabitFormContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHabitScreen(
    viewModel: EditHabitViewModel,
    onBackClick: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val backContentDescription = stringResource(R.string.back)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is EditHabitUiEvent.SaveFailed -> snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    uiState.navigateBackRequestId?.let { requestId ->
        LaunchedEffect(requestId) {
            viewModel.onNavigateBackConsumed(requestId)
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_habit)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.semantics {
                            contentDescription = backContentDescription
                        }
                    ) {
                        Text("‹")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.loadError != null -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    Text(uiState.loadError)
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    HabitFormContent(
                        name = uiState.name,
                        targetPerWeek = uiState.targetPerWeek,
                        nameError = uiState.nameError,
                        isSaving = uiState.isSaving,
                        onNameChanged = viewModel::onNameChanged,
                        onDecreaseTarget = viewModel::decreaseTargetPerWeek,
                        onIncreaseTarget = viewModel::increaseTargetPerWeek,
                        onSave = viewModel::saveHabit,
                        saveLabel = stringResource(R.string.save_changes)
                    )
                }
            }
        }
    }
}
