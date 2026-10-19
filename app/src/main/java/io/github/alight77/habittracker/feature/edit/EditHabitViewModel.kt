package io.github.alight77.habittracker.feature.edit

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.icon.HabitIconMatcher
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.domain.model.WeeklyTargetConstraints.MAX_TARGET_PER_WEEK
import io.github.alight77.habittracker.domain.model.WeeklyTargetConstraints.MIN_TARGET_PER_WEEK
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditHabitUiState(
    val name: String = "",
    // 原始未知 Key 同样保存在草稿中；仅在展示时由 Catalog 回退。
    val iconKey: IconKey = IconKey.GENERAL,
    val targetPerWeek: Int = MAX_TARGET_PER_WEEK,
    @param:StringRes val nameErrorResId: Int? = null,
    val isLoading: Boolean = true,
    @param:StringRes val loadErrorResId: Int? = null,
    val isSaving: Boolean = false,
    val navigateBackRequestId: Long? = null
)

sealed interface EditHabitUiEvent {
    data class SaveFailed(@param:StringRes val messageResId: Int) : EditHabitUiEvent
}

class EditHabitViewModel(
    private val repository: HabitRepository,
    private val habitId: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditHabitUiState())
    private val _events = MutableSharedFlow<EditHabitUiEvent>(extraBufferCapacity = 1)
    private var loadedHabit: HabitEntity? = null
    private var nextNavigationRequestId = 0L

    val uiState: StateFlow<EditHabitUiState> = _uiState.asStateFlow()
    val events: SharedFlow<EditHabitUiEvent> = _events.asSharedFlow()

    init {
        loadHabit()
    }

    fun retry() {
        loadHabit()
    }

    fun onNameChanged(name: String) {
        _uiState.update { state ->
            if (state.isLoading || state.isSaving || state.loadErrorResId != null) state
            else state.copy(name = name, nameErrorResId = null)
        }
    }

    fun selectIcon(key: IconKey) {
        _uiState.update { state ->
            if (state.isLoading || state.isSaving || state.loadErrorResId != null || key !in IconKey.known) state
            else state.copy(iconKey = key)
        }
    }

    fun recommendIconForCurrentName() {
        _uiState.update { state ->
            if (state.isLoading || state.isSaving || state.loadErrorResId != null) state
            else state.copy(iconKey = HabitIconMatcher.match(state.name))
        }
    }

    fun decreaseTargetPerWeek() {
        _uiState.update { state ->
            if (state.isSaving) state else state.copy(targetPerWeek = (state.targetPerWeek - 1).coerceAtLeast(MIN_TARGET_PER_WEEK))
        }
    }

    fun increaseTargetPerWeek() {
        _uiState.update { state ->
            if (state.isSaving) state else state.copy(targetPerWeek = (state.targetPerWeek + 1).coerceAtMost(MAX_TARGET_PER_WEEK))
        }
    }

    fun saveHabit() {
        val state = _uiState.value
        val normalizedName = state.name.trim()
        val habit = loadedHabit ?: return

        if (normalizedName.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(nameErrorResId = R.string.habit_name_required)
            }
            return
        }

        if (state.isLoading || state.isSaving || state.loadErrorResId != null) return

        _uiState.update { currentState ->
            currentState.copy(isSaving = true, nameErrorResId = null)
        }

        viewModelScope.launch(dispatcher) {
            try {
                repository.updateHabit(
                    habit.copy(
                        name = normalizedName,
                        targetPerWeek = state.targetPerWeek,
                        iconKey = state.iconKey.value
                    )
                )
                _uiState.value = EditHabitUiState(
                    navigateBackRequestId = ++nextNavigationRequestId
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error

                _uiState.update { currentState ->
                    currentState.copy(isSaving = false)
                }
                _events.emit(EditHabitUiEvent.SaveFailed(R.string.habit_save_failed))
            }
        }
    }

    fun onNavigateBackConsumed(requestId: Long) {
        _uiState.update { state ->
            if (state.navigateBackRequestId == requestId) {
                state.copy(navigateBackRequestId = null)
            } else {
                state
            }
        }
    }

    private fun loadHabit() {
        _uiState.value = EditHabitUiState(isLoading = true)

        viewModelScope.launch(dispatcher) {
            try {
                val habit = repository.getHabitById(habitId)
                if (habit == null) {
                    _uiState.value = EditHabitUiState(
                        isLoading = false,
                        loadErrorResId = R.string.edit_habit_not_found
                    )
                    return@launch
                }

                loadedHabit = habit
                _uiState.value = EditHabitUiState(
                    name = habit.name,
                    iconKey = IconKey(habit.iconKey),
                    targetPerWeek = habit.targetPerWeek,
                    isLoading = false
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error

                _uiState.value = EditHabitUiState(
                    isLoading = false,
                    loadErrorResId = R.string.edit_habit_load_failed
                )
            }
        }
    }
}

class EditHabitViewModelFactory(
    private val repository: HabitRepository,
    private val habitId: Int
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditHabitViewModel::class.java)) {
            return EditHabitViewModel(repository, habitId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
