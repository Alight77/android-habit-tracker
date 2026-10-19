package io.github.alight77.habittracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.core.time.systemTodayFlow
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.domain.icon.IconKey
import io.github.alight77.habittracker.domain.usecase.calculateStreak
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.domain.usecase.epochDayToLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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

    // 本地点击目标同步保留到失败或日期切换，防止 Room 已提交但 Compose 尚未重组时读回旧值。
    private val optimisticChecked =
        MutableStateFlow<Map<Pair<Int, LocalDate>, CheckedOverride>>(emptyMap())
    private val retryRequest = MutableStateFlow(0)
    private val _events = MutableSharedFlow<DashboardUiEvent>(extraBufferCapacity = 1)

    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            setTodayHabitChecked.events.collect { event ->
                when (event) {
                    is SetTodayHabitCheckedUseCase.DomainEvent.OptimisticApplied -> {
                        applyOptimisticEvent(
                            event.habitId, event.today, event.targetChecked, event.intentId
                        )
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Confirmed -> {
                        clearOptimisticEvent(event.habitId, event.today, event.intentId, failed = false)
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Reverted -> {
                        if (clearOptimisticEvent(event.habitId, event.today, event.intentId, failed = true)) {
                            emitFailureMessage(event)
                        }
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Superseded -> {
                        applyOptimisticEvent(
                            event.habitId, event.today, event.newTargetChecked, event.intentId
                        )
                    }

                    is SetTodayHabitCheckedUseCase.DomainEvent.Rejected -> {
                        if (clearOptimisticEvent(event.habitId, event.today, event.intentId, failed = true)) {
                            emitFailureMessage(event)
                        }
                    }
                }
            }
        }
    }

    private suspend fun emitFailureMessage(event: SetTodayHabitCheckedUseCase.DomainEvent) {
        val messageResId = dashboardMessageFor(event) ?: return
        _events.emit(DashboardUiEvent.ShowResourceMessage(messageResId))
    }

    private fun applyOptimisticEvent(
        habitId: Int,
        date: LocalDate,
        targetChecked: Boolean,
        intentId: Long
    ) {
        val key = habitId to date
        optimisticChecked.update { current ->
            val latest = current[key]
            // 延迟到达的旧事件不能覆盖主线程刚记录的更新点击意图。
            if (latest != null && latest.intentId >= intentId) current
            else current + (key to CheckedOverride(targetChecked, intentId, retainUntilDateChange = false))
        }
    }

    private suspend fun clearOptimisticEvent(
        habitId: Int,
        date: LocalDate,
        intentId: Long,
        failed: Boolean
    ): Boolean {
        val key = habitId to date
        val tracked = optimisticChecked.value[key]
        // ViewModel 重建后可能没收到旧的 OptimisticApplied；当日有效失败仍须提示。
        if (tracked == null) return failed && date == todayFlow.value && date == currentDate()
        if (tracked.intentId != intentId) return false
        val persistedChecked = if (failed) {
            try {
                // 失败后的单条 Room 查询可先于仪表盘全量 Flow 返回，避免下一次点击读到旧页面快照。
                recordDao.getRecordByDate(habitId, date.toEpochDay())?.isDone == true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        } else null
        while (true) {
            val current = optimisticChecked.value
            val latest = current[key]
            // 读库可能挂起；只允许仍是当前意图的失败事件回滚并提示。
            if (latest?.intentId != intentId) return false
            val updated = when {
                !failed && latest.retainUntilDateChange -> current
                failed && persistedChecked != null -> current + (
                    key to latest.copy(
                        targetChecked = persistedChecked,
                        retainUntilDateChange = true
                    )
                )
                else -> current - key
            }
            if (optimisticChecked.compareAndSet(current, updated)) return true
        }
    }

    fun onHabitToggle(habitId: Int) {
        val today = currentDate()
        val displayedState = uiState.value as? DashboardUiState.Success
        // 日期流可能先于 UI StateFlow 更新；必须核对生成卡片状态的日期，再读取点击基线。
        if (today != todayFlow.value || (displayedState != null && displayedState.today != today)) {
            requestTodayRefresh()
            _events.tryEmit(DashboardUiEvent.ShowResourceMessage(R.string.check_in_date_changed))
            return
        }

        val key = habitId to today
        val visibleChecked = displayedState?.items?.firstOrNull { it.id == habitId }
            ?.isDoneToday ?: return
        val targetChecked = !(optimisticChecked.value[key]?.targetChecked ?: visibleChecked)
        val intentId = setTodayHabitChecked.nextIntentId()
        // 点击处理在主线程无挂起地更新目标；事件收集、IO 调度和 Compose 重组均可晚于下一次点击。
        optimisticChecked.update { current ->
            current.filter { (dateKey, value) ->
                dateKey.second == today || !value.retainUntilDateChange
            } + (key to CheckedOverride(targetChecked, intentId, retainUntilDateChange = true))
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            setTodayHabitChecked(
                SetTodayHabitCheckedUseCase.Command(habitId, targetChecked, today, intentId)
            )
        }
    }

    fun refreshToday() {
        requestTodayRefresh()
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            try {
                val deletedRows = repository.deleteHabitById(habitId)
                // 仅在 Room 确认删掉一行后提示成功；重复确认产生的零行删除不算成功。
                if (deletedRows > 0) {
                    optimisticChecked.update { current ->
                        current.filterKeys { key -> key.first != habitId }
                    }
                    _events.emit(DashboardUiEvent.ShowResourceMessage(R.string.habit_delete_success))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _events.emit(DashboardUiEvent.ShowResourceMessage(R.string.habit_delete_failed))
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
                        val mergedDoneToday = optimistic[habit.id to today]?.targetChecked ?: dbDoneToday
                        val persistedDoneDates = doneRecordsForHabit.map { record ->
                            epochDayToLocalDate(record.date)
                        }
                        val effectiveDoneDates = persistedDoneDates.filterNot { date -> date == today } +
                            if (mergedDoneToday) listOf(today) else emptyList()
                        val streak = calculateStreak(effectiveDoneDates, today)
                        val recentDays = recentCheckInDays(
                            createdDate = Instant.ofEpochMilli(habit.createdAt)
                                .atZone(zoneId)
                                .toLocalDate(),
                            doneDates = effectiveDoneDates,
                            today = today
                        )

                        // 保留数据库中的原始 Key；未知类别仅由展示目录回退，不能在读取时降级数据。
                        HabitItemUiState(
                            id = habit.id,
                            name = habit.name,
                            targetPerWeek = habit.targetPerWeek,
                            isDoneToday = mergedDoneToday,
                            streak = streak,
                            recentDays = recentDays,
                            iconKey = IconKey(habit.iconKey)
                        )
                    }

                    val state: DashboardUiState = DashboardUiState.Success(items, today)
                    state
                }.catch {
                    emit(DashboardUiState.Error(R.string.dashboard_load_failed))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState.Loading
            )

    private data class CheckedOverride(
        val targetChecked: Boolean,
        val intentId: Long,
        val retainUntilDateChange: Boolean
    )
}
