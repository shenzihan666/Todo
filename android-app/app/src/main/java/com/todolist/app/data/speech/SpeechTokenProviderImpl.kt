package com.todolist.app.data.speech

import com.todolist.app.data.network.dto.AuthResponse
import com.todolist.app.data.network.dto.RefreshRequest
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.domain.speech.SpeechTokenProvider
import com.todolist.app.ui.settings.buildServerBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SpeechTokenProviderImpl(
    private val prefs: UserPreferencesRepository,
    private val json: Json,
    private val plainClient: OkHttpClient,
) : SpeechTokenProvider {

    override fun getAccessToken(): String = prefs.getCachedAccessToken()

    private fun baseUrlOrNull(): String? {
        val ip = prefs.getCachedServerIp().trim()
        if (ip.isEmpty()) return null
        return buildServerBaseUrl(ip)
    }

    override suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val refresh = prefs.getCachedRefreshToken().trim()
        if (refresh.isEmpty()) return@withContext null
        val base = baseUrlOrNull() ?: return@withContext null

        val body = json.encodeToString(
            RefreshRequest.serializer(),
            RefreshRequest(refreshToken = refresh),
        )
        val refreshRequest = Request.Builder()
            .url("${base}api/v1/auth/refresh")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val refreshResponse = plainClient.newCall(refreshRequest).execute()
        if (!refreshResponse.isSuccessful) {
            refreshResponse.close()
            return@withContext null
        }

        val responseBody = refreshResponse.body?.string() ?: return@withContext null
        val authResponse = json.decodeFromString(AuthResponse.serializer(), responseBody)
        val newAccess = authResponse.accessToken ?: return@withContext null
        val newRefresh = authResponse.refreshToken ?: refresh
        prefs.updateTokens(newAccess, newRefresh)
        newAccess
    }

    override suspend fun onRefreshFailed() {
        prefs.clearAuth()
    }
}
