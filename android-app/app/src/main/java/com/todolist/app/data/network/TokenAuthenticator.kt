package com.todolist.app.data.network

import com.todolist.app.data.network.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val getBaseUrl: () -> String,
    private val getRefreshToken: () -> String,
    private val onTokenRefreshed: suspend (accessToken: String, refreshToken: String) -> Unit,
    private val onRefreshFailed: suspend () -> Unit,
    private val json: Json,
    private val plainClient: OkHttpClient,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            runBlocking { onRefreshFailed() }
            return null
        }

        val refreshToken = getRefreshToken()
        if (refreshToken.isEmpty()) {
            runBlocking { onRefreshFailed() }
            return null
        }

        val body = json.encodeToString(
            RefreshRequest.serializer(),
            RefreshRequest(refreshToken = refreshToken),
        )
        val refreshRequest = Request.Builder()
            .url("${getBaseUrl()}api/v1/auth/refresh")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val refreshResponse = plainClient.newCall(refreshRequest).execute()
        if (!refreshResponse.isSuccessful) {
            refreshResponse.close()
            runBlocking { onRefreshFailed() }
            return null
        }

        val responseBody = refreshResponse.body?.string() ?: run {
            runBlocking { onRefreshFailed() }
            return null
        }

        val authResponse = json.decodeFromString(
            com.todolist.app.data.network.dto.AuthResponse.serializer(),
            responseBody,
        )

        val newAccess = authResponse.accessToken ?: run {
            runBlocking { onRefreshFailed() }
            return null
        }
        val newRefresh = authResponse.refreshToken ?: refreshToken

        runBlocking { onTokenRefreshed(newAccess, newRefresh) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
