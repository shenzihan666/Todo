package com.todolist.app.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.todolist.app.ui.auth.AuthRoute
import com.todolist.app.ui.home.HomeRoute
import com.todolist.app.ui.settings.SettingsRoute

private const val SETTINGS_MOTION_MS = 340

private val settingsTween =
    tween<Float>(durationMillis = SETTINGS_MOTION_MS, easing = FastOutSlowInEasing)

@Composable
fun TodoListNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Auth.route,
        modifier = modifier,
    ) {
        composable(route = Screen.Auth.route) {
            AuthRoute(
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(
            route = Screen.Home.route,
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -(fullWidth / 5) },
                ) + fadeOut(animationSpec = settingsTween)
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -(fullWidth / 5) },
                ) + fadeIn(animationSpec = settingsTween)
            },
        ) {
            HomeRoute(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Screen.Settings.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                ) + fadeIn(animationSpec = settingsTween)
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                ) + fadeOut(animationSpec = settingsTween)
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -(fullWidth / 5) },
                ) + fadeIn(animationSpec = settingsTween)
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                ) + fadeOut(animationSpec = settingsTween)
            },
        ) {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
    }
}
