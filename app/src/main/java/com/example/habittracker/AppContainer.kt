package com.example.habittracker

import android.content.Context
import androidx.room.Room
import com.example.habittracker.data.local.HabitDatabase
import com.example.habittracker.data.local.HabitDatabaseMigrations
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase

class AppContainer(context: Context) {

    private val database = Room.databaseBuilder(
        context.applicationContext,
        HabitDatabase::class.java,
        "habits.db"
    )
        .addMigrations(
            HabitDatabaseMigrations.MIGRATION_2_3,
            HabitDatabaseMigrations.MIGRATION_3_4
        )
        .build()

    val repository = HabitRepository(
        database.habitDao(),
        database.recordDao()
    )

    private val setTodayHabitCheckedUseCase = SetTodayHabitCheckedUseCase(
        applyCommand = { command ->
            repository.setTodayRecordChecked(
                habitId = command.habitId,
                epochDay = command.today.toEpochDay(),
                targetChecked = command.targetChecked
            )
            SetTodayHabitCheckedUseCase.ApplyResult.Accepted
        },
        observeSourceChecked = { command ->
            repository.observeTodayRecord(
                habitId = command.habitId,
                epochDay = command.today.toEpochDay()
            )
        }
    )

    val viewModelFactory = HabitTrackerViewModelFactory(
        repository = repository,
        habitDao = database.habitDao(),
        recordDao = database.recordDao(),
        setTodayHabitChecked = setTodayHabitCheckedUseCase
    )
}
