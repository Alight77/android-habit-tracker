package io.github.alight77.habittracker.feature.stats

import androidx.annotation.StringRes

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(
        val summary: StatsSummary,
        val availableHabits: List<StatsHabitOption> = emptyList(),
        val selectedHabitId: Int? = null,
        val weeklyTrend: List<WeeklyCheckInTrendPoint> = emptyList()
    ) : StatsUiState
    data class Error(@param:StringRes val messageResId: Int) : StatsUiState
}

data class StatsHabitOption(val id: Int, val name: String)
