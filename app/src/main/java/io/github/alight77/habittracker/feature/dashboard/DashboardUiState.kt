package io.github.alight77.habittracker.feature.dashboard

import androidx.compose.runtime.Immutable
import io.github.alight77.habittracker.domain.usecase.RecentGoalProgress
import java.time.LocalDate

sealed interface  DashboardUiState {

    object Loading : DashboardUiState

    data class Success(
        val items: List<HabitItemUiState>,
        /** 与卡片状态同时计算的日期，供点击时校验快照是否仍为今日。 */
        val today: LocalDate
    ) : DashboardUiState

    data class Error(
        val message: String
    ) : DashboardUiState
}

@Immutable
data class HabitItemUiState(
    val id: Int,
    val name: String,
    val targetPerWeek: Int,
    val isDoneToday: Boolean,
    val streak: Int,
    val goalProgress: RecentGoalProgress
)
