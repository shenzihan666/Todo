package com.todolist.app.navigation

sealed class Screen(val route: String) {
    data object Health : Screen("health")
    data object Settings : Screen("settings")
}
