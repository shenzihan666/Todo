package com.todolist.app.data.repository

import com.todolist.app.data.network.dto.HealthResponse
import com.todolist.app.domain.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class HealthRepositoryImpl(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : HealthRepository {

    override suspend fun checkHealth(baseUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = baseUrl.trim().trimEnd('/') + "/"
                val url = "${root}api/v1/health"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body?.string() ?: return@withContext false
                    val parsed = json.decodeFromString<HealthResponse>(body)
                    parsed.status == "ok"
                }
            }.getOrDefault(false)
        }
}
