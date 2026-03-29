package com.todolist.app.domain.model

/** Machine-readable failure; UI maps to localized strings via `stringResource`. */
sealed interface HealthFailureReason {
    data class BadServerStatus(val status: String) : HealthFailureReason
    data object CannotConnect : HealthFailureReason
    data object Timeout : HealthFailureReason
    data class UnknownHost(val host: String) : HealthFailureReason
    data class Generic(val message: String?) : HealthFailureReason
}

sealed interface HealthCheckResult {
    data object Connected : HealthCheckResult
    data class Failed(val reason: HealthFailureReason) : HealthCheckResult
}
