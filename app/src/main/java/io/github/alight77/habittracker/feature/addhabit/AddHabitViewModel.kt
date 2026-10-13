package io.github.alight77.habittracker.feature.addhabit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class AddHabitUiState(
    val name: String = "",
    val manualIconKey: IconKey? = null,
    val targetPerWeek: Int = MAX_TARGET_PER_WEEK,
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val navigateBackRequestId: Long? = null
) {
    val effectiveIconKey: IconKey get() = manualIconKey ?: HabitIconMatcher.match(name)
}

sealed interface AddHabitUiEvent {
    data class SaveFailed(val message: String) : AddHabitUiEvent
}

class AddHabitViewModel(
    private val repository: HabitRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddHabitUiState())
    private val _events = MutableSharedFlow<AddHabitUiEvent>(extraBufferCapacity = 1)
    private var nextNavigationRequestId = 0L

    val uiState: StateFlow<AddHabitUiState> = _uiState.asStateFlow()
    val events: SharedFlow<AddHabitUiEvent> = _events.asSharedFlow()

    fun onNameChanged(name: String) {
        _uiState.update { state ->
            if (state.isSaving) state else state.copy(name = name, nameError = null)
        }
    }

    fun selectIcon(key: IconKey) {
        _uiState.update { state ->
            if (state.isSaving || key !in IconKey.known) state else state.copy(manualIconKey = key)
        }
    }

    fun restoreAutomaticIcon() {
        _uiState.update { state ->
            if (state.isSaving) state else state.copy(manualIconKey = null)
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

        if (normalizedName.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(nameError = "请输入习惯名称")
            }
            return
        }

        if (state.isSaving) return

        _uiState.update { currentState ->
            currentState.copy(isSaving = true, nameError = null)
        }

        viewModelScope.launch(dispatcher) {
            try {
                repository.addHabit(
                    HabitEntity(
                        name = normalizedName,
                        description = "",
                        targetPerWeek = state.targetPerWeek,
                        // 保存使用点击瞬间的表单快照；后续名称变化不会改变本次写入的图标。
                        iconKey = state.effectiveIconKey.value,
                        createdAt = System.currentTimeMillis()
                    )
                )
                _uiState.value = AddHabitUiState(
                    navigateBackRequestId = ++nextNavigationRequestId
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error

                _uiState.update { currentState ->
                    currentState.copy(isSaving = false)
                }
                _events.emit(AddHabitUiEvent.SaveFailed("保存失败，请稍后重试"))
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
}
