package com.todolist.app.data.repository

import com.todolist.app.data.network.ApiService
import com.todolist.app.data.network.dto.LoginRequest
import com.todolist.app.data.network.dto.RefreshRequest
import com.todolist.app.data.network.dto.RegisterRequest
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.ui.settings.buildServerBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AuthRepository(
    private val userPreferences: UserPreferencesRepository,
    private val apiServiceFactory: (String) -> ApiService,
) {

    suspend fun register(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = resolveBaseUrl()
                    ?: throw IllegalStateException("Set server IP in Settings first")
                val api = apiServiceFactory(baseUrl)
                val response = api.register(RegisterRequest(username = username, password = password))
                val access = response.accessToken ?: throw IllegalStateException("Missing access_token")
                val refresh = response.refreshToken ?: throw IllegalStateException("Missing refresh_token")
                val tenantId = response.tenantId ?: throw IllegalStateException("Missing tenant_id")
                val name = response.username ?: username
                userPreferences.saveAuthData(access, refresh, tenantId, name)
            }
        }

    suspend fun login(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = resolveBaseUrl()
                    ?: throw IllegalStateException("Set server IP in Settings first")
                val api = apiServiceFactory(baseUrl)
                val response = api.login(LoginRequest(username = username, password = password))
                val access = response.accessToken ?: throw IllegalStateException("Missing access_token")
                val refresh = response.refreshToken ?: throw IllegalStateException("Missing refresh_token")
                val tenantId = response.tenantId ?: throw IllegalStateException("Missing tenant_id")
                val name = response.username ?: username
                userPreferences.saveAuthData(access, refresh, tenantId, name)
            }
        }

    suspend fun logout(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = resolveBaseUrl()
                val refresh = userPreferences.getRefreshTokenBlocking()
                if (baseUrl != null && refresh.isNotEmpty()) {
                    runCatching {
                        val api = apiServiceFactory(baseUrl)
                        api.logout(RefreshRequest(refreshToken = refresh))
                    }
                }
                userPreferences.clearAuth()
            }
        }

    private suspend fun resolveBaseUrl(): String? {
        val ip = userPreferences.serverIp.first().trim()
        if (ip.isEmpty()) return null
        return buildServerBaseUrl(ip)
    }
}
