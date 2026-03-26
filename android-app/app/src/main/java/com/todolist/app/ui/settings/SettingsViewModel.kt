package com.todolist.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.domain.model.HealthCheckResult
import com.todolist.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val DEFAULT_SERVER_PORT = 8000

fun buildServerBaseUrl(host: String, port: Int = DEFAULT_SERVER_PORT): String {
    val h = host.trim()
    if (h.isEmpty()) return ""
    return "http://$h:$port/"
}

enum class ServerSettingsError {
    EmptyServerIp,
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data object Checking : ConnectionUiState
    data object Connected : ConnectionUiState
    data class Failed(val reason: String) : ConnectionUiState
}

class SettingsViewModel(
    private val healthRepository: HealthRepository,
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    private val _draftServerIp = MutableStateFlow("")
    val draftServerIp: StateFlow<String> = _draftServerIp.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()

    private val _validationError = MutableStateFlow<ServerSettingsError?>(null)
    val validationError: StateFlow<ServerSettingsError?> = _validationError.asStateFlow()

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
}
