package io.github.alight77.habittracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.habittracker.core.time.systemTodayFlow
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.usecase.calculateStreak
import io.github.alight77.habittracker.domain.usecase.calculateRecentGoalProgress
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.domain.usecase.epochDayToLocalDate
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
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val habitDao: HabitDao,
    private val recordDao: RecordDao,
    private val repository: HabitRepository,
    private val setTodayHabitChecked: SetTodayHabitCheckedUseCase,
    todaySource: Flow<LocalDate> = systemTodayFlow(),
    private val currentDate: () -> LocalDate = { LocalDate.now() },
    private val requestTodayRefresh: () -> Unit = {}
) : ViewModel() {

    private val todayFlow: StateFlow<LocalDate> = todaySource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalDate.now()
    )

    private val recordsFlow = recordDao.getAllRecords()
    private val habitsFlow = habitDao.getAllHabits()

    // 旧日期的终态事件只能清理旧日期目标，不能清除跨午夜后今日的乐观状态。
    private val optimisticChecked = MutableStateFlow<Map<Pair<Int, LocalDate>, Boolean>>(emptyMap())
    private val retryRequest = MutableStateFlow(0)
    private val _events = MutableSharedFlow<DashboardUiEvent>(extraBufferCapacity = 1)

    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            setTodayHabitChecked.events.collect { event ->
                when (event) {
                    is SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied -> {
                        optimisticChecked.update {
                            it + ((event.habitId to event.today) to event.targetChecked)
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed -> {
                        optimisticChecked.update {
                            it - (event.habitId to event.today)
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Reverted -> {
                        optimisticChecked.update {
                            it - (event.habitId to event.today)
                        }
                        emitFailureMessage(event)
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Superseded -> {
                        optimisticChecked.update {
                            it + ((event.habitId to event.today) to event.newTargetChecked)
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Rejected -> {
                        optimisticChecked.update {
                            it - (event.habitId to event.today)
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
        val today = currentDate()
        // 页面日期尚未收敛时拒绝本次点击，避免向昨天的 Room 记录写入今日意图。
        if (today != todayFlow.value) {
            requestTodayRefresh()
            _events.tryEmit(DashboardUiEvent.ShowMessage("日期已更新，请重新操作"))
            return
        }

        viewModelScope.launch {
            setTodayHabitChecked(
                SetTodayHabitCheckedUseCase.Command(
                    habitId = habitId,
                    targetChecked = targetChecked,
                    today = today
                )
            )
        }
    }

    fun refreshToday() {
        requestTodayRefresh()
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
                    val zoneId = ZoneId.systemDefault()
                    val items = habits.map { habit ->
                        val recordsForHabit = recordsByHabitId[habit.id] ?: emptyList()
                        val doneRecordsForHabit = recordsForHabit.filter { it.isDone }
                        val dbDoneToday = recordsForHabit.any { record ->
                            epochDayToLocalDate(record.date) == today && record.isDone
                        }
                        val mergedDoneToday = optimistic[habit.id to today] ?: dbDoneToday
                        val persistedDoneDates = doneRecordsForHabit.map { record ->
                            epochDayToLocalDate(record.date)
                        }
                        val effectiveDoneDates = persistedDoneDates.filterNot { date -> date == today } +
                            if (mergedDoneToday) listOf(today) else emptyList()
                        val streak = calculateStreak(effectiveDoneDates, today)
                        val goalProgress = calculateRecentGoalProgress(
                            targetPerWeek = habit.targetPerWeek,
                            createdDate = Instant.ofEpochMilli(habit.createdAt)
                                .atZone(zoneId)
                                .toLocalDate(),
                            doneDates = effectiveDoneDates,
                            today = today
                        )

                        HabitItemUiState(
                            habit.id,
                            habit.name,
                            habit.targetPerWeek,
                            mergedDoneToday,
                            streak,
                            goalProgress
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
