package com.todolist.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.data.repository.AuthRepository
import com.todolist.app.i18n.AppLocale
import com.todolist.app.domain.model.HealthCheckResult
import com.todolist.app.domain.model.HealthFailureReason
import com.todolist.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val DEFAULT_SERVER_PORT = 8000

fun buildServerBaseUrl(host: String, port: Int = DEFAULT_SERVER_PORT): String {
    val h = host.trim()
    if (h.isEmpty()) return ""
    return "http://$h:$port/"
}

/** WebSocket URL for speech-to-text (must match server `/api/v1/speech/ws`). */
fun buildSpeechWebSocketUrl(
    host: String,
    port: Int = DEFAULT_SERVER_PORT,
    accessToken: String? = null,
): String {
    val h = host.trim()
    if (h.isEmpty()) return ""
    val base = "ws://$h:$port/api/v1/speech/ws"
    val token = accessToken?.trim().orEmpty()
    if (token.isEmpty()) return base
    val encoded = java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8)
    return "$base?access_token=$encoded"
}

enum class ServerSettingsError {
    EmptyServerIp,
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data object Checking : ConnectionUiState
    data object Connected : ConnectionUiState
    data class Failed(val reason: HealthFailureReason) : ConnectionUiState
}

class SettingsViewModel(
    private val healthRepository: HealthRepository,
    private val userPreferences: UserPreferencesRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _draftServerIp = MutableStateFlow("")
    val draftServerIp: StateFlow<String> = _draftServerIp.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()

    private val _validationError = MutableStateFlow<ServerSettingsError?>(null)
    val validationError: StateFlow<ServerSettingsError?> = _validationError.asStateFlow()

    val currentUsername: StateFlow<String> =
        userPreferences.username.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            "",
        )

    val appLocale: StateFlow<AppLocale> =
        userPreferences.appLocale.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppLocale.SYSTEM,
        )

    init {
        viewModelScope.launch {
            _draftServerIp.value = userPreferences.serverIp.first()
        }
    }

    fun onServerIpChange(value: String) {
        _draftServerIp.value = value
        _validationError.value = null
    }

    fun testConnection() {
        viewModelScope.launch {
            val ip = _draftServerIp.value.trim()
            if (ip.isEmpty()) {
                _validationError.value = ServerSettingsError.EmptyServerIp
                return@launch
            }
            _validationError.value = null
            userPreferences.setServerIp(ip)
            _connectionState.value = ConnectionUiState.Checking
            val baseUrl = buildServerBaseUrl(ip)
            val result = healthRepository.checkHealth(baseUrl)
            _connectionState.value = when (result) {
                is HealthCheckResult.Connected -> ConnectionUiState.Connected
                is HealthCheckResult.Failed -> ConnectionUiState.Failed(result.reason)
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun setAppLocale(locale: AppLocale) {
        viewModelScope.launch {
            userPreferences.setAppLocale(locale)
        }
    }
}
