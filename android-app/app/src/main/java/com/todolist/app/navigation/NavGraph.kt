package com.todolist.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.todolist.app.ui.health.HealthRoute

@Composable
fun TodoListNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Health.route,
        modifier = modifier,
    ) {
        composable(Screen.Health.route) {
            HealthRoute()
        }
    }
}
