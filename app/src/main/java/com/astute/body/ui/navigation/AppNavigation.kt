package com.astute.body.ui.navigation

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
import com.astute.body.ui.exercise.ExerciseDetailScreen
import com.astute.body.ui.exercise.ManageExercisesScreen
import com.astute.body.ui.history.HistoryScreen
import com.astute.body.ui.home.HomeScreen
import com.astute.body.ui.muscles.MuscleOverviewScreen
import com.astute.body.ui.settings.SettingsScreen
import com.astute.body.ui.workout.ActiveWorkoutScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object WorkoutRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute
@Serializable data class ExerciseDetailRoute(val exerciseId: String)
@Serializable data object MusclesRoute
@Serializable data object ManageExercisesRoute

data class TopLevelRoute<T : Any>(
    val name: String,
    val route: T,
    val icon: ImageVector
)

val topLevelRoutes = listOf(
    TopLevelRoute("Home", HomeRoute, Icons.Default.Home),
    TopLevelRoute("Muscles", MusclesRoute, Icons.Default.FitnessCenter),
    TopLevelRoute("History", HistoryRoute, Icons.Default.History),
    TopLevelRoute("Settings", SettingsRoute, Icons.Default.Settings),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = topLevelRoutes.any { topLevelRoute ->
        currentDestination?.hierarchy?.any {
            it.hasRoute(topLevelRoute.route::class)
        } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                    onResumeWorkout = {
                        navController.navigate(WorkoutRoute)
                    },
                    onNavigateToSettings = {
                        navController.navigate(SettingsRoute)
                    },
                    onNavigateToExerciseDetail = { exerciseId ->
                        navController.navigate(ExerciseDetailRoute(exerciseId))
                    }
                )
            }
            composable<MusclesRoute> {
                MuscleOverviewScreen()
            }
            composable<WorkoutRoute> {
                ActiveWorkoutScreen(
                    onWorkoutComplete = {
                        navController.navigate(HomeRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                        }
                    },
                    onNavigateToExerciseDetail = { exerciseId ->
                        navController.navigate(ExerciseDetailRoute(exerciseId))
                    }
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onNavigateToExerciseDetail = { exerciseId ->
                        navController.navigate(ExerciseDetailRoute(exerciseId))
                    }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onNavigateToManageExercises = {
                        navController.navigate(ManageExercisesRoute)
                    }
                )
            }
            composable<ManageExercisesRoute> {
                ManageExercisesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<ExerciseDetailRoute> {
                ExerciseDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
