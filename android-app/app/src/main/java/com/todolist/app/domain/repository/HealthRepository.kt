package com.todolist.app.domain.repository

interface HealthRepository {
    suspend fun checkHealth(): Boolean
}
