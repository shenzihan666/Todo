package com.todolist.app.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    /** Main voice / chat home screen (distinct from the HTTP GET /api/v1/health check). */
    data object Home : Screen("home")
    data object Settings : Screen("settings")
}
