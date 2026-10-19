package io.github.alight77.habittracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import io.github.alight77.habittracker.feature.dashboard.DashboardViewModel
import io.github.alight77.habittracker.feature.stats.StatsViewModel
import io.github.alight77.habittracker.ui.navigation.HabitNavHost
import io.github.alight77.habittracker.ui.theme.HabitTrackerTheme

class MainActivity : ComponentActivity() {

    internal val appContainer: AppContainer
        get() = (application as HabitTrackerApplication).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dashboardViewModel = ViewModelProvider(this, appContainer.viewModelFactory)[DashboardViewModel::class.java]
        val statsViewModel = ViewModelProvider(this, appContainer.viewModelFactory)[StatsViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            HabitTrackerTheme {
                val navController = rememberNavController()

                HabitNavHost(
                    navController = navController,
                    dashboardViewModel = dashboardViewModel,
                    addHabitViewModelFactory = appContainer.viewModelFactory,
                    statsViewModel = statsViewModel,
                    repository = appContainer.repository
                )
            }
        }
    }
}
