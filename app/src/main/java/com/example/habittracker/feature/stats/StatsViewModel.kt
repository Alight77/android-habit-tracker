package com.example.habittracker.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.feature.dashboard.systemTodayFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StatsViewModel(
    habitDao: HabitDao,
    recordDao: RecordDao,
    todaySource: Flow<LocalDate> = systemTodayFlow()
) : ViewModel() {

    private val todayFlow = todaySource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalDate.now()
    )

    val uiState: StateFlow<StatsUiState> = combine(
        habitDao.getAllHabits(),
        recordDao.getAllRecords(),
        todayFlow
    ) { habits, records, today ->
        StatsUiState.Success(
            summary = calculateStats(
                habits = habits,
                records = records,
                today = today
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState.Loading
    )
}