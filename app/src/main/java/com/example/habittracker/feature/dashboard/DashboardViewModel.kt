package com.example.habittracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.domain.usecase.calculateStreak
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.domain.usecase.epochMillisToLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(
    private val habitDao: HabitDao,
    private val recordDao: RecordDao,
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

    val uiState: StateFlow<DashboardUiState> =
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
                    epochMillisToLocalDate(record.date) == today && record.isDone
                }
                val mergedDoneToday = optimistic[habit.id] ?: dbDoneToday
                val dates = doneRecordsForHabit.map { record -> epochMillisToLocalDate(record.date) }
                val streak = calculateStreak(dates, today)

                HabitItemUiState(habit.id, habit.name, habit.targetPerWeek, mergedDoneToday, streak)
            }

            DashboardUiState.Success(items)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState.Loading
            )
}