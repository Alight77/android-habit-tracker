package io.github.alight77.habittracker.feature.stats

import androidx.annotation.StringRes

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(val summary: StatsSummary) : StatsUiState
    data class Error(@param:StringRes val messageResId: Int) : StatsUiState
}
