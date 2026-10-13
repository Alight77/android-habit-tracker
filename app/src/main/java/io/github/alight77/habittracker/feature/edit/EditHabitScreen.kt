package io.github.alight77.habittracker.feature.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.component.HabitFormContent
import io.github.alight77.habittracker.ui.component.HabitFormHero
import io.github.alight77.habittracker.ui.component.HabitFormPageScaffold

@Composable
fun EditHabitScreen(
    viewModel: EditHabitViewModel,
    onBackClick: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

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

    HabitFormPageScaffold(
        title = stringResource(R.string.edit_habit),
        backgroundIllustrationRes = R.drawable.habit_form_edit_vignette,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick
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
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(uiState.loadError)
                    Button(onClick = viewModel::retry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        // 与新增页一致：先消费 Scaffold Insets，再仅为键盘补足空间以保持滚动可达。
                        .consumeWindowInsets(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HabitFormHero(
                        title = stringResource(R.string.edit_habit_intro_title),
                        guidance = stringResource(R.string.edit_habit_guidance),
                        illustrationRes = R.drawable.habit_form_journal
                    )
                    HabitFormContent(
                        name = uiState.name,
                        targetPerWeek = uiState.targetPerWeek,
                        nameError = uiState.nameError,
                        isSaving = uiState.isSaving,
                        onNameChanged = viewModel::onNameChanged,
                        onDecreaseTarget = viewModel::decreaseTargetPerWeek,
                        onIncreaseTarget = viewModel::increaseTargetPerWeek,
                        onSave = viewModel::saveHabit,
                        saveLabel = stringResource(R.string.save_changes),
                        showNameSuggestions = false
                    )
                }
            }
        }
    }
}
