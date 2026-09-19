package com.example.habittracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.habittracker.data.repository.HabitRepository
import com.example.habittracker.feature.dashboard.DashboardScreen
import com.example.habittracker.feature.dashboard.DashboardViewModel
import com.example.habittracker.feature.edit.EditHabitViewModel
import com.example.habittracker.feature.edit.EditHabitViewModelFactory
import com.example.habittracker.feature.stats.StatsScreen
import com.example.habittracker.feature.stats.StatsViewModel
import com.example.habittracker.ui.screen.AddHabitScreen
import com.example.habittracker.ui.screen.EditHabitScreen
import com.example.habittracker.viewmodel.HabitViewModel

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_ADD_HABIT = "addHabit"
private const val ROUTE_STATS = "stats"
private const val ROUTE_EDIT_HABIT = "editHabit/{habitId}"

private fun editHabitRoute(habitId: Int): String = "editHabit/$habitId"

@Composable
fun HabitNavHost(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel,
    addHabitViewModel: HabitViewModel,
    statsViewModel: StatsViewModel,
    repository: HabitRepository
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
                },
                onEditClick = { habitId ->
                    navController.navigate(editHabitRoute(habitId))
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

        composable(
            route = ROUTE_EDIT_HABIT,
            arguments = listOf(navArgument("habitId") { type = NavType.IntType })
        ) { backStackEntry ->
            val habitId = requireNotNull(backStackEntry.arguments?.getInt("habitId"))
            val editHabitViewModel = remember(backStackEntry, habitId) {
                ViewModelProvider(
                    backStackEntry,
                    EditHabitViewModelFactory(repository, habitId)
                )[EditHabitViewModel::class.java]
            }

            EditHabitScreen(
                viewModel = editHabitViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
