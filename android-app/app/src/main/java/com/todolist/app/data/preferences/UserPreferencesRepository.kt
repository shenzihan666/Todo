package com.todolist.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private val SERVER_IP_KEY = stringPreferencesKey("server_ip")
private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
private val TENANT_ID_KEY = stringPreferencesKey("tenant_id")
private val USERNAME_KEY = stringPreferencesKey("username")
private val AGENT_THREAD_ID_KEY = stringPreferencesKey("agent_thread_id")

class UserPreferencesRepository(
    private val context: Context,
) {

    /** Mirrors DataStore for synchronous reads from OkHttp (no runBlocking on the interceptor thread). */
    private val cachedAccessToken = AtomicReference("")
    private val cachedRefreshToken = AtomicReference("")
    private val cachedServerIp = AtomicReference("")

    val serverIp: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_IP_KEY].orEmpty()
    }

    suspend fun setServerIp(ip: String) {
        val trimmed = ip.trim()
        context.dataStore.edit { prefs ->
            prefs[SERVER_IP_KEY] = trimmed
        }
        cachedServerIp.set(trimmed)
    }

    val accessToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN_KEY].orEmpty()
    }

    val refreshToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[REFRESH_TOKEN_KEY].orEmpty()
    }

    val tenantId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TENANT_ID_KEY].orEmpty()
    }

    val username: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY].orEmpty()
    }

    /** LangGraph agent thread id (UUID string); persisted for chat history after app restart. */
    val agentThreadId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGENT_THREAD_ID_KEY].orEmpty()
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[ACCESS_TOKEN_KEY].isNullOrEmpty()
    }

    suspend fun saveAuthData(
        accessToken: String,
        refreshToken: String,
        tenantId: String,
        username: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
            prefs[TENANT_ID_KEY] = tenantId
            prefs[USERNAME_KEY] = username
        }
        cachedAccessToken.set(accessToken)
        cachedRefreshToken.set(refreshToken)
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
        cachedAccessToken.set(accessToken)
        cachedRefreshToken.set(refreshToken)
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(TENANT_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(AGENT_THREAD_ID_KEY)
        }
        cachedAccessToken.set("")
        cachedRefreshToken.set("")
    }

    suspend fun setAgentThreadId(threadId: String) {
        val trimmed = threadId.trim()
        context.dataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(AGENT_THREAD_ID_KEY)
            } else {
                prefs[AGENT_THREAD_ID_KEY] = trimmed
            }
        }
    }

    /**
     * Loads tokens and server IP from [DataStore] into memory. Call once at startup (e.g. [android.app.Application.onCreate])
     * so OkHttp interceptors can read synchronously.
     */
    suspend fun hydrateFromDataStore() {
        val prefs = context.dataStore.data.first()
        cachedAccessToken.set(prefs[ACCESS_TOKEN_KEY].orEmpty())
        cachedRefreshToken.set(prefs[REFRESH_TOKEN_KEY].orEmpty())
        cachedServerIp.set(prefs[SERVER_IP_KEY].orEmpty())
    }

    fun getCachedAccessToken(): String = cachedAccessToken.get()

    fun getCachedRefreshToken(): String = cachedRefreshToken.get()

    fun getCachedServerIp(): String = cachedServerIp.get()

    suspend fun getAccessTokenBlocking(): String =
        context.dataStore.data.first()[ACCESS_TOKEN_KEY].orEmpty()

    suspend fun getRefreshTokenBlocking(): String =
        context.dataStore.data.first()[REFRESH_TOKEN_KEY].orEmpty()
}
