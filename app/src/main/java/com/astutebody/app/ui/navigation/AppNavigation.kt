package com.astutebody.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.astutebody.app.ui.history.HistoryScreen
import com.astutebody.app.ui.home.HomeScreen
import com.astutebody.app.ui.settings.SettingsScreen
import com.astutebody.app.ui.workout.ActiveWorkoutScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object WorkoutRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

data class TopLevelRoute<T : Any>(
    val name: String,
    val route: T,
    val icon: ImageVector
)

val topLevelRoutes = listOf(
    TopLevelRoute("Home", HomeRoute, Icons.Default.Home),
    TopLevelRoute("Workout", WorkoutRoute, Icons.Default.FitnessCenter),
    TopLevelRoute("History", HistoryRoute, Icons.Default.History),
    TopLevelRoute("Settings", SettingsRoute, Icons.Default.Settings),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                topLevelRoutes.forEach { topLevelRoute ->
                    NavigationBarItem(
                        icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                        label = { Text(topLevelRoute.name) },
                        selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(topLevelRoute.route::class)
                        } == true,
                        onClick = {
                            navController.navigate(topLevelRoute.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onStartWorkout = {
                        navController.navigate(WorkoutRoute)
                    },
                    onNavigateToSettings = {
                        navController.navigate(SettingsRoute)
                    }
                )
            }
            composable<WorkoutRoute> {
                ActiveWorkoutScreen(
                    onWorkoutComplete = {
                        navController.navigate(HomeRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
            composable<HistoryRoute> {
                HistoryScreen()
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
        }
    }
}
