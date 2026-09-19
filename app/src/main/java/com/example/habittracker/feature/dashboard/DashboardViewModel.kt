package com.example.habittracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.usecase.calculateStreak
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.domain.usecase.epochDayToLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val habitDao: HabitDao,
    private val recordDao: RecordDao,
    private val repository: HabitRepository,
    private val setTodayHabitChecked: SetTodayHabitCheckedUseCase,
    todaySource: Flow<LocalDate> = systemTodayFlow()
) : ViewModel() {

    private val todayFlow: StateFlow<LocalDate> = todaySource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalDate.now()
    )

    private val recordsFlow = recordDao.getAllRecords()
    private val habitsFlow = habitDao.getAllHabits()

    private val optimisticChecked = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val retryRequest = MutableStateFlow(0)
    private val _events = MutableSharedFlow<DashboardUiEvent>(extraBufferCapacity = 1)

    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            setTodayHabitChecked.events.collect { event ->
                when (event) {
                    is SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied -> {
                        optimisticChecked.update {
                            it + (event.habitId to event.targetChecked)
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed -> {
                        optimisticChecked.update {
                            it - event.habitId
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Reverted -> {
                        optimisticChecked.update {
                            it - event.habitId
                        }
                        emitFailureMessage(event)
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Superseded -> {
                        optimisticChecked.update {
                            it + (event.habitId to event.newTargetChecked)
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Rejected -> {
                        optimisticChecked.update {
                            it - event.habitId
                        }
                        emitFailureMessage(event)
                    }
                }
            }
        }
    }

    private suspend fun emitFailureMessage(event: SetTodayHabitCheckedUseCase.DomainEvent) {
        val message = dashboardMessageFor(event) ?: return
        _events.emit(DashboardUiEvent.ShowMessage(message))
    }

    fun onHabitChecked(habitId: Int, targetChecked: Boolean) {
        viewModelScope.launch {
            val today = todayFlow.value
            setTodayHabitChecked(
                SetTodayHabitCheckedUseCase.Command(
                    habitId = habitId,
                    targetChecked = targetChecked,
                    today = today
                )
            )
        }
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteHabitById(habitId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _events.emit(DashboardUiEvent.ShowMessage("删除习惯失败，请稍后重试"))
            }
        }
    }

    fun retry() {
        retryRequest.update { request -> request + 1 }
    }

    val uiState: StateFlow<DashboardUiState> =
        retryRequest
            .flatMapLatest {
                combine(
                    habitsFlow,
                    recordsFlow,
                    todayFlow,
                    optimisticChecked
                ) { habits, records, today, optimistic ->
                    val recordsByHabitId = records.groupBy { it.habitId }
                    val items = habits.map { habit ->
                        val recordsForHabit = recordsByHabitId[habit.id] ?: emptyList()
                        val doneRecordsForHabit = recordsForHabit.filter { it.isDone }
                        val dbDoneToday = recordsForHabit.any { record ->
                            epochDayToLocalDate(record.date) == today && record.isDone
                        }
                        val mergedDoneToday = optimistic[habit.id] ?: dbDoneToday
                        val dates = doneRecordsForHabit.map { record -> epochDayToLocalDate(record.date) }
                        val streak = calculateStreak(dates, today)

                        HabitItemUiState(
                            habit.id,
                            habit.name,
                            habit.targetPerWeek,
                            mergedDoneToday,
                            streak
                        )
                    }

                    val state: DashboardUiState = DashboardUiState.Success(items)
                    state
                }.catch {
                    emit(DashboardUiState.Error("加载习惯数据失败，请重试"))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState.Loading
            )
}
