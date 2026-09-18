package com.example.habittracker.feature.dashboard

import android.os.Message
import androidx.compose.runtime.Immutable

sealed interface  DashboardUiState {

    object Loading : DashboardUiState

    data class Success(
        val items: List<HabitItemUiState>
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
    val streak: Int
)