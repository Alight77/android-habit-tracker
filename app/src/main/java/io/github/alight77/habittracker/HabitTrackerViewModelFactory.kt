package io.github.alight77.habittracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.alight77.habittracker.data.local.HabitDao
import io.github.alight77.habittracker.data.local.RecordDao
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.core.time.TodayDateSource
import io.github.alight77.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import io.github.alight77.habittracker.feature.addhabit.AddHabitViewModel
import io.github.alight77.habittracker.feature.dashboard.DashboardViewModel
import io.github.alight77.habittracker.feature.stats.StatsViewModel

class HabitTrackerViewModelFactory(
    private val repository: HabitRepository,
    private val habitDao: HabitDao,
    private val recordDao: RecordDao,
    private val setTodayHabitChecked: SetTodayHabitCheckedUseCase,
    private val todayDateSource: TodayDateSource
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
                    setTodayHabitChecked = setTodayHabitChecked,
                    todaySource = todayDateSource.dates,
                    currentDate = todayDateSource::today,
                    requestTodayRefresh = todayDateSource::refresh
                )
            }

            modelClass.isAssignableFrom(StatsViewModel::class.java) -> {
                StatsViewModel(
                    habitDao = habitDao,
                    recordDao = recordDao,
                    todaySource = todayDateSource.dates,
                    requestTodayRefresh = todayDateSource::refresh
                )
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
