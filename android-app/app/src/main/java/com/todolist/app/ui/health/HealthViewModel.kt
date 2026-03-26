package com.todolist.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.domain.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConnectionUiState {
    data object Checking : ConnectionUiState
    data object Connected : ConnectionUiState
    data object Offline : ConnectionUiState
}

class HealthViewModel(
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Checking)
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = ConnectionUiState.Checking
            val ok = withContext(Dispatchers.IO) {
                runCatching { healthRepository.checkHealth() }.getOrDefault(false)
            }
            _state.value = if (ok) ConnectionUiState.Connected else ConnectionUiState.Offline
        }
    }
}
