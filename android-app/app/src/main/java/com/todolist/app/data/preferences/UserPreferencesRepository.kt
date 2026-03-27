package com.todolist.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private val SERVER_IP_KEY = stringPreferencesKey("server_ip")
private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
private val TENANT_ID_KEY = stringPreferencesKey("tenant_id")
private val USERNAME_KEY = stringPreferencesKey("username")

class UserPreferencesRepository(
    private val context: Context,
) {

    val serverIp: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_IP_KEY].orEmpty()
    }

    suspend fun setServerIp(ip: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_IP_KEY] = ip.trim()
        }
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
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(TENANT_ID_KEY)
            prefs.remove(USERNAME_KEY)
        }
    }

    suspend fun getAccessTokenBlocking(): String =
        context.dataStore.data.first()[ACCESS_TOKEN_KEY].orEmpty()

    suspend fun getRefreshTokenBlocking(): String =
        context.dataStore.data.first()[REFRESH_TOKEN_KEY].orEmpty()
}
