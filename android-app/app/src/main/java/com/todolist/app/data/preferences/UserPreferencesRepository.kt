package com.todolist.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private val SERVER_IP_KEY = stringPreferencesKey("server_ip")

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
}
