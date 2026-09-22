package com.example.habittracker.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.model.WeeklyTargetConstraints.MAX_TARGET_PER_WEEK
import com.example.habittracker.domain.model.WeeklyTargetConstraints.MIN_TARGET_PER_WEEK
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
    val targetPerWeek: Int = MAX_TARGET_PER_WEEK,
    val nameError: String? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isSaving: Boolean = false,
    val navigateBackRequestId: Long? = null
)

sealed interface EditHabitUiEvent {
    data class SaveFailed(val message: String) : EditHabitUiEvent
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
            state.copy(name = name, nameError = null)
        }
    }

    fun decreaseTargetPerWeek() {
        _uiState.update { state ->
            state.copy(targetPerWeek = (state.targetPerWeek - 1).coerceAtLeast(MIN_TARGET_PER_WEEK))
        }
    }

    fun increaseTargetPerWeek() {
        _uiState.update { state ->
            state.copy(targetPerWeek = (state.targetPerWeek + 1).coerceAtMost(MAX_TARGET_PER_WEEK))
        }
    }

    fun saveHabit() {
        val state = _uiState.value
        val normalizedName = state.name.trim()
        val habit = loadedHabit ?: return

        if (normalizedName.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(nameError = "请输入习惯名称")
            }
            return
        }

        if (state.isLoading || state.isSaving || state.loadError != null) return

        _uiState.update { currentState ->
            currentState.copy(isSaving = true, nameError = null)
        }

        viewModelScope.launch(dispatcher) {
            try {
                repository.updateHabit(
                    habit.copy(
                        name = normalizedName,
                        targetPerWeek = state.targetPerWeek
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
                _events.emit(EditHabitUiEvent.SaveFailed("保存失败，请稍后重试"))
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
                        loadError = "未找到该习惯"
                    )
                    return@launch
                }

                loadedHabit = habit
                _uiState.value = EditHabitUiState(
                    name = habit.name,
                    targetPerWeek = habit.targetPerWeek,
                    isLoading = false
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error

                _uiState.value = EditHabitUiState(
                    isLoading = false,
                    loadError = "加载习惯失败，请稍后重试"
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
