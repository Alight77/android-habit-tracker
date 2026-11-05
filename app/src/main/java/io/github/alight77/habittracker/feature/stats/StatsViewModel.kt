package io.github.alight77.habittracker.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.HabitEntity
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.local.RecordEntity
import io.github.alight77.habittracker.core.time.systemTodayFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    habitDao: HabitDao,
    recordDao: RecordDao,
    todaySource: Flow<LocalDate> = systemTodayFlow(),
    private val requestTodayRefresh: () -> Unit = {}
) : ViewModel() {

    private val todayFlow = todaySource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalDate.now()
    )
    private val retryRequest = MutableStateFlow(0)
    private val selectedHabitId = MutableStateFlow<Int?>(null)

    private val sourceFlow: Flow<StatsSourceState> = retryRequest
        .flatMapLatest {
            combine(
                habitDao.getAllHabits(),
                recordDao.getAllRecords(),
                todayFlow
            ) { habits, records, today ->
                val source: StatsSourceState = StatsSourceState.Ready(
                    StatsSnapshot(
                        habits = habits,
                        records = records,
                        today = today,
                        summary = calculateStats(habits, records, today)
                    )
                )
                source
            }.catch {
                emit(StatsSourceState.Failed)
            }
        }

    val uiState: StateFlow<StatsUiState> = combine(sourceFlow, selectedHabitId) { source, requestedId ->
        when (source) {
            StatsSourceState.Failed -> SelectionResolution(
                state = StatsUiState.Error(R.string.stats_load_failed),
                requestedId = requestedId,
                resolvedId = requestedId
            )
            is StatsSourceState.Ready -> resolveSelection(source.snapshot, requestedId)
        }
    }
        .onEach { resolution ->
            // 只在默认选择或删除回退时提交；并发的手动选择不能被旧快照覆盖。
            if (resolution.requestedId != resolution.resolvedId) {
                selectedHabitId.compareAndSet(resolution.requestedId, resolution.resolvedId)
            }
        }
        .map { resolution -> resolution.state }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState.Loading
        )

    fun onHabitSelected(habitId: Int) {
        val state = uiState.value as? StatsUiState.Success ?: return
        if (state.availableHabits.any { habit -> habit.id == habitId }) {
            selectedHabitId.value = habitId
        }
    }

    fun retry() {
        retryRequest.update { request -> request + 1 }
    }

    fun refreshToday() {
        requestTodayRefresh()
    }

    private fun resolveSelection(snapshot: StatsSnapshot, requestedId: Int?): SelectionResolution {
        val sortedHabits = snapshot.habits.sortedWith(
            compareByDescending<HabitEntity> { habit -> habit.createdAt }
                .thenByDescending { habit -> habit.id }
        )
        val selectedHabit = sortedHabits.firstOrNull { habit -> habit.id == requestedId }
            ?: sortedHabits.firstOrNull()
        val trend = selectedHabit?.let { habit ->
            calculateWeeklyCheckInTrend(
                today = snapshot.today,
                habitId = habit.id,
                createdDate = Instant.ofEpochMilli(habit.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                records = snapshot.records
            )
        }.orEmpty()
        return SelectionResolution(
            state = StatsUiState.Success(
                summary = snapshot.summary,
                availableHabits = sortedHabits.map { habit -> StatsHabitOption(habit.id, habit.name) },
                selectedHabitId = selectedHabit?.id,
                weeklyTrend = trend
            ),
            requestedId = requestedId,
            resolvedId = selectedHabit?.id
        )
    }
}

private sealed interface StatsSourceState {
    data class Ready(val snapshot: StatsSnapshot) : StatsSourceState
    data object Failed : StatsSourceState
}

private data class StatsSnapshot(
    val habits: List<HabitEntity>,
    val records: List<RecordEntity>,
    val today: LocalDate,
    val summary: StatsSummary
)

private data class SelectionResolution(
    val state: StatsUiState,
    val requestedId: Int?,
    val resolvedId: Int?
)
