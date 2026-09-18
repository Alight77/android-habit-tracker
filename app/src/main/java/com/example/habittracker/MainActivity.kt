package com.example.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.habittracker.data.local.HabitDatabase
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.domain.usecase.dashboard.SetTodayHabitCheckedUseCase
import com.example.habittracker.feature.dashboard.DashboardViewModel
import com.example.habittracker.feature.stats.StatsViewModel
import com.example.habittracker.ui.navigation.HabitNavHost
import com.example.habittracker.ui.theme.HabitTrackerTheme
import com.example.habittracker.viewmodel.HabitViewModel
import kotlinx.coroutines.flow.map
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            HabitDatabase::class.java,
            "habits.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        val repository = HabitRepository(
            db.habitDao(),
            db.recordDao()
        )

        val setTodayHabitCheckedUseCase = SetTodayHabitCheckedUseCase(
            applyCommand = { command ->
                val todayMillis = command.today
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                repository.setTodayRecordChecked(
                    habitId = command.habitId,
                    date = todayMillis,
                    targetChecked = command.targetChecked
                )

                SetTodayHabitCheckedUseCase.ApplyResult.Accepted
            },
            observeSourceChecked = { command ->
                val todayMillis = command.today
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                db.recordDao()
                    .observeRecordByDate(command.habitId, todayMillis)
                    .map { record -> record?.isDone == true }
            }
        )

        val addHabitViewModel = HabitViewModel(repository)
        val dashboardViewModel = DashboardViewModel(
            habitDao = db.habitDao(),
            recordDao = db.recordDao(),
            setTodayHabitChecked = setTodayHabitCheckedUseCase
        )
        val statsViewModel = StatsViewModel(
            habitDao = db.habitDao(),
            recordDao = db.recordDao()
        )

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()

            HabitNavHost(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                addHabitViewModel = addHabitViewModel,
                statsViewModel = statsViewModel
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