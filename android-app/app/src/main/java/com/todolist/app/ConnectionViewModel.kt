package com.todolist.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface ConnectionUiState {
    data object Checking : ConnectionUiState
    data object Connected : ConnectionUiState
    data object Offline : ConnectionUiState
}

class ConnectionViewModel : ViewModel() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Checking)
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = ConnectionUiState.Checking
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(BuildConfig.HEALTH_URL)
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use false
                        val body = response.body?.string().orEmpty()
                        runCatching {
                            JSONObject(body).getString("status") == "ok"
                        }.getOrDefault(false)
                    }
                }.getOrDefault(false)
            }
            _state.value = if (ok) ConnectionUiState.Connected else ConnectionUiState.Offline
        }
    }
}
