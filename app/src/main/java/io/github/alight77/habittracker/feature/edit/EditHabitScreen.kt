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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.component.HabitFormContent
import io.github.alight77.habittracker.ui.component.HabitFormHero
import io.github.alight77.habittracker.ui.component.HabitIconPicker
import io.github.alight77.habittracker.ui.component.HabitFormPageScaffold
import io.github.alight77.habittracker.ui.icon.HabitIconCatalog

@Composable
fun EditHabitScreen(
    viewModel: EditHabitViewModel,
    onBackClick: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val showIconPicker = remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentContext = rememberUpdatedState(LocalContext.current)
    val resolvedIcon = HabitIconCatalog.resolveForDisplay(uiState.iconKey)
    val iconStatus = if (resolvedIcon.originalKey != resolvedIcon.definition.key) {
        stringResource(R.string.habit_icon_unknown_status)
    } else {
        stringResource(R.string.habit_icon_saved_status, stringResource(resolvedIcon.definition.labelRes))
    }

    if (showIconPicker.value && !uiState.isLoading && !uiState.isSaving && uiState.loadErrorResId == null) {
        HabitIconPicker(
            selectedKey = uiState.iconKey,
            onSelect = { key ->
                viewModel.selectIcon(key)
                showIconPicker.value = false
            },
            onDismiss = { showIconPicker.value = false }
        )
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    // 收集器跨重组继续运行；事件消费时读取当前界面语言的资源。
                    is EditHabitUiEvent.SaveFailed -> snackbarHostState.showSnackbar(
                        currentContext.value.getString(event.messageResId)
                    )
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

            uiState.loadErrorResId != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(uiState.loadErrorResId))
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
                        nameError = uiState.nameErrorResId?.let { stringResource(it) },
                        isSaving = uiState.isSaving,
                        onNameChanged = viewModel::onNameChanged,
                        onDecreaseTarget = viewModel::decreaseTargetPerWeek,
                        onIncreaseTarget = viewModel::increaseTargetPerWeek,
                        onSave = viewModel::saveHabit,
                        saveLabel = stringResource(R.string.save_changes),
                        showNameSuggestions = false,
                        iconKey = uiState.iconKey,
                        iconStatus = iconStatus,
                        onChooseIcon = {
                            keyboardController?.hide()
                            showIconPicker.value = true
                        },
                        iconActionLabel = stringResource(R.string.habit_icon_recommend_now),
                        onIconAction = viewModel::recommendIconForCurrentName
                    )
                }
            }
        }
    }
}
