package com.example.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.habittracker.data.local.HabitDatabase
import com.example.habittracker.data.local.HabitDatabaseMigrations
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.feature.dashboard.DashboardViewModel
import com.example.habittracker.feature.stats.StatsViewModel
import com.example.habittracker.ui.navigation.HabitNavHost
import com.example.habittracker.ui.theme.HabitTrackerTheme
import com.example.habittracker.viewmodel.HabitViewModel
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            HabitDatabase::class.java,
            "habits.db"
        )
            .addMigrations(
                HabitDatabaseMigrations.MIGRATION_2_3,
                HabitDatabaseMigrations.MIGRATION_3_4
            )
            .build()

        val repository = HabitRepository(
            db.habitDao(),
            db.recordDao()
        )

        val setTodayHabitCheckedUseCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                val epochDay = command.today.toEpochDay()

                repository.setTodayRecordChecked(
                    habitId = command.habitId,
                    epochDay = epochDay,
                    targetChecked = command.targetChecked
                )

                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                val epochDay = command.today.toEpochDay()

                db.recordDao()
                    .observeRecordByDate(command.habitId, epochDay)
                    .map { record -> record?.isDone == true }
            }
        )

        val viewModelFactory = HabitTrackerViewModelFactory(
            repository = repository,
            habitDao = db.habitDao(),
            recordDao = db.recordDao(),
            setTodayHabitChecked = setTodayHabitCheckedUseCase
        )
        val addHabitViewModel = ViewModelProvider(this, viewModelFactory)[HabitViewModel::class.java]
        val dashboardViewModel = ViewModelProvider(this, viewModelFactory)[DashboardViewModel::class.java]
        val statsViewModel = ViewModelProvider(this, viewModelFactory)[StatsViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()

            HabitNavHost(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                addHabitViewModel = addHabitViewModel,
                statsViewModel = statsViewModel,
                repository = repository
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HabitTrackerTheme {
        Greeting("Android")
    }
}
