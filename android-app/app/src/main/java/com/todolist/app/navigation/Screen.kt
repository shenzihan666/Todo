package com.todolist.app.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    /** Logged-in shell: bottom tabs (voice home, schedule, bills, settings). */
    data object Main : Screen("main")
    data object Settings : Screen("settings")
}
