package com.todolist.app.domain.repository

import com.todolist.app.domain.model.HealthCheckResult

interface HealthRepository {
    suspend fun checkHealth(baseUrl: String): HealthCheckResult
}
