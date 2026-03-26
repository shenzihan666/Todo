package com.todolist.app.domain.repository

interface HealthRepository {
    /** @param baseUrl e.g. `http://192.168.1.1:8000/` (trailing slash optional) */
    suspend fun checkHealth(baseUrl: String): Boolean
}
