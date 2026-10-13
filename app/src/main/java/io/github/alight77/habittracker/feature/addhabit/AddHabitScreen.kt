package io.github.alight77.habittracker.feature.addhabit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.component.HabitFormContent
import io.github.alight77.habittracker.ui.component.HabitFormHero
import io.github.alight77.habittracker.ui.component.HabitIconPicker
import io.github.alight77.habittracker.ui.component.HabitFormPageScaffold
import io.github.alight77.habittracker.ui.icon.HabitIconCatalog

@Composable
fun AddHabitScreen(
    viewModel: AddHabitViewModel,
    onBackClick: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val showIconPicker = remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val iconDefinition = HabitIconCatalog.resolveForDisplay(uiState.effectiveIconKey).definition
    val iconStatus = stringResource(
        if (uiState.manualIconKey == null) R.string.habit_icon_auto_status else R.string.habit_icon_manual_status,
        stringResource(iconDefinition.labelRes)
    )

    if (showIconPicker.value && !uiState.isSaving) {
        HabitIconPicker(
            selectedKey = uiState.effectiveIconKey,
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
                    is AddHabitUiEvent.SaveFailed -> snackbarHostState.showSnackbar(event.message)
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
        title = stringResource(R.string.add_habit),
        backgroundIllustrationRes = R.drawable.habit_form_add_vignette,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Scaffold 已处理系统栏；消费该 Insets 后只为 IME 预留剩余高度，避免底部重复留白。
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HabitFormHero(
                title = stringResource(R.string.add_habit_intro_title),
                guidance = stringResource(R.string.add_habit_guidance),
                illustrationRes = R.drawable.habit_form_seedling
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
                saveLabel = stringResource(R.string.save_habit),
                showNameSuggestions = true,
                iconKey = uiState.effectiveIconKey,
                iconStatus = iconStatus,
                onChooseIcon = {
                    keyboardController?.hide()
                    showIconPicker.value = true
                },
                iconActionLabel = if (uiState.manualIconKey != null) stringResource(R.string.habit_icon_restore_auto) else null,
                onIconAction = viewModel::restoreAutomaticIcon
            )
        }
    }
}
