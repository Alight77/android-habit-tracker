package io.github.alight77.habittracker.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.core.time.systemTodayFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
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
    private val retryRequest = MutableStateFlow(0)

    val uiState: StateFlow<StatsUiState> = retryRequest
        .flatMapLatest {
            combine(
                habitDao.getAllHabits(),
                recordDao.getAllRecords(),
                todayFlow
            ) { habits, records, today ->
                val state: StatsUiState = StatsUiState.Success(
                    summary = calculateStats(
                        habits = habits,
                        records = records,
                        today = today
                    )
                )
                state
            }.catch {
                emit(StatsUiState.Error("加载统计数据失败，请重试"))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState.Loading
        )

    fun retry() {
        retryRequest.update { request -> request + 1 }
    }
}
