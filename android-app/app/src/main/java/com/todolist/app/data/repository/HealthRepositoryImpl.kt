package com.todolist.app.data.repository

import com.todolist.app.data.network.ApiService
import com.todolist.app.domain.model.HealthCheckResult
import com.todolist.app.domain.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HealthRepositoryImpl(
    private val apiServiceFactory: (String) -> ApiService,
) : HealthRepository {

    override suspend fun checkHealth(baseUrl: String): HealthCheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val api = apiServiceFactory(baseUrl)
                val response = api.getHealth()
                if (response.status == "ok") {
                    HealthCheckResult.Connected
                } else {
                    HealthCheckResult.Failed("Server status: ${response.status}")
                }
            }.getOrElse { e ->
                HealthCheckResult.Failed(e.toUserMessage())
            }
        }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is ConnectException -> "Cannot connect to server"
    is SocketTimeoutException -> "Connection timed out"
    is UnknownHostException -> "Unknown host: $message"
    else -> localizedMessage ?: "Unknown error"
}
