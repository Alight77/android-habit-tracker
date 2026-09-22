package io.github.alight77.habittracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.alight77.habittracker.data.repository.HabitRepository
import io.github.alight77.habittracker.feature.addhabit.AddHabitScreen
import io.github.alight77.habittracker.feature.addhabit.AddHabitViewModel
import io.github.alight77.habittracker.feature.dashboard.DashboardScreen
import io.github.alight77.habittracker.feature.dashboard.DashboardViewModel
import io.github.alight77.habittracker.feature.edit.EditHabitScreen
import io.github.alight77.habittracker.feature.edit.EditHabitViewModel
import io.github.alight77.habittracker.feature.edit.EditHabitViewModelFactory
import io.github.alight77.habittracker.feature.stats.StatsScreen
import io.github.alight77.habittracker.feature.stats.StatsViewModel

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_ADD_HABIT = "addHabit"
private const val ROUTE_STATS = "stats"
private const val ROUTE_EDIT_HABIT = "editHabit/{habitId}"

private fun editHabitRoute(habitId: Int): String = "editHabit/$habitId"

@Composable
fun HabitNavHost(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel,
    addHabitViewModel: AddHabitViewModel,
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
