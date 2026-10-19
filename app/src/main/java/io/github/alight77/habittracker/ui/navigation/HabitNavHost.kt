package io.github.alight77.habittracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
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

private const val ROUTE_ADD_HABIT = "addHabit"
private const val ROUTE_EDIT_HABIT = "editHabit/{habitId}"

private fun editHabitRoute(habitId: Int): String = "editHabit/$habitId"

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    if (currentDestination?.route == destination.route) return
    navigate(destination.route) {
        launchSingleTop = true
        restoreState = true
        // 只保留首页作为活动返回路径；切换时保存另一 Tab 的可保存页面状态。
        popUpTo(graph.findStartDestination().id) { saveState = true }
    }
}

@Composable
fun HabitNavHost(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel,
    addHabitViewModelFactory: ViewModelProvider.Factory,
    statsViewModel: StatsViewModel,
    repository: HabitRepository
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Today.route
    ) {
        composable(TopLevelDestination.Today.route) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onAddClick = {
                    navController.navigate(ROUTE_ADD_HABIT)
                },
                onEditClick = { habitId ->
                    navController.navigate(editHabitRoute(habitId))
                },
                bottomBar = {
                    HabitBottomBar(TopLevelDestination.Today, navController::navigateToTopLevel)
                }
            )
        }

        composable(ROUTE_ADD_HABIT) { backStackEntry ->
            val addHabitViewModel = remember(backStackEntry) {
                ViewModelProvider(backStackEntry, addHabitViewModelFactory)[AddHabitViewModel::class.java]
            }
            val exitRequested = remember(backStackEntry) { mutableStateOf(false) }
            val leaveAddHabit = {
                val state = addHabitViewModel.uiState.value
                // 写入未结束时不能销毁持有协程的导航条目；成功写入后的返回请求由页面消费后再弹栈。
                if (!exitRequested.value && !state.isSaving && state.navigateBackRequestId == null) {
                    exitRequested.value = true
                    navController.popBackStack()
                }
            }
            BackHandler { leaveAddHabit() }
            AddHabitScreen(
                viewModel = addHabitViewModel,
                onBackClick = leaveAddHabit
            )
        }

        composable(TopLevelDestination.Stats.route) {
            StatsScreen(
                viewModel = statsViewModel,
                bottomBar = {
                    HabitBottomBar(TopLevelDestination.Stats, navController::navigateToTopLevel)
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
