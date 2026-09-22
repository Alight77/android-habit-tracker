package io.github.alight77.habittracker.feature.stats

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(val summary: StatsSummary) : StatsUiState
    data class Error(val message: String) : StatsUiState
}
