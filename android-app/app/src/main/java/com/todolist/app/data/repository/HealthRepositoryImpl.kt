package com.todolist.app.data.repository

import com.todolist.app.data.network.ApiService
import com.todolist.app.domain.repository.HealthRepository

class HealthRepositoryImpl(
    private val api: ApiService,
) : HealthRepository {

    override suspend fun checkHealth(): Boolean =
        runCatching {
            api.getHealth().status == "ok"
        }.getOrDefault(false)
}
