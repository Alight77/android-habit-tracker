package com.example.habittracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habittracker.data.local.HabitEntity
import com.example.habittracker.data.repository.HabitRepository
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
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val navigateBackRequestId: Long? = null
)

sealed interface AddHabitUiEvent {
    data class SaveFailed(val message: String) : AddHabitUiEvent
}

class HabitViewModel(
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
            state.copy(name = name, nameError = null)
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
                        targetPerWeek = 7,
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
