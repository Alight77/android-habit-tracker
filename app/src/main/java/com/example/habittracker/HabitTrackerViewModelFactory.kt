package com.example.habittracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.habittracker.data.local.HabitDao
import com.example.habittracker.data.local.RecordDao
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.feature.addhabit.AddHabitViewModel
import com.example.habittracker.feature.dashboard.DashboardViewModel
import com.example.habittracker.feature.stats.StatsViewModel

class HabitTrackerViewModelFactory(
    private val repository: HabitRepository,
    private val habitDao: HabitDao,
    private val recordDao: RecordDao,
    private val setTodayHabitChecked: SetTodayHabitCheckedUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AddHabitViewModel::class.java) -> {
                AddHabitViewModel(repository)
            }

            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    habitDao = habitDao,
                    recordDao = recordDao,
                    repository = repository,
                    setTodayHabitChecked = setTodayHabitChecked
                )
            }

            modelClass.isAssignableFrom(StatsViewModel::class.java) -> {
                StatsViewModel(
                    habitDao = habitDao,
                    recordDao = recordDao
                )
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
