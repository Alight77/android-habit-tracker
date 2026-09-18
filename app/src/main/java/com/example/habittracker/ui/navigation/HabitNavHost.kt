package com.example.habittracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.habittracker.feature.dashboard.DashboardScreen
import com.example.habittracker.feature.dashboard.DashboardViewModel
import com.example.habittracker.feature.stats.StatsScreen
import com.example.habittracker.feature.stats.StatsViewModel
import com.example.habittracker.ui.screen.AddHabitScreen
import com.example.habittracker.viewmodel.HabitViewModel

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_ADD_HABIT = "addHabit"
private const val ROUTE_STATS = "stats"

@Composable
fun HabitNavHost(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel,
    addHabitViewModel: HabitViewModel,
    statsViewModel: StatsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onAddClick = {
                    navController.navigate(ROUTE_ADD_HABIT)
                },
                onStatsClick = {
                    navController.navigate(ROUTE_STATS)
                }
            )
        }

        composable(ROUTE_ADD_HABIT) {
            AddHabitScreen(
                viewModel = addHabitViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(ROUTE_STATS) {
            StatsScreen(
                viewModel = statsViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}