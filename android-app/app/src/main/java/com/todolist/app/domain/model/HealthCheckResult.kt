package com.todolist.app.domain.model

sealed interface HealthCheckResult {
    data object Connected : HealthCheckResult
    data class Failed(val reason: String) : HealthCheckResult
}
