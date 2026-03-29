package com.todolist.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.R
import com.todolist.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode {
    Login,
    Register,
}

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.Login,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun setMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(mode = mode, errorMessage = null)
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        val user = state.username.trim()
        val pass = state.password
        val app = getApplication<Application>()
        if (user.isEmpty()) {
            _uiState.value = state.copy(errorMessage = app.getString(R.string.auth_error_username_required))
            return
        }
        if (pass.length < 6) {
            _uiState.value = state.copy(errorMessage = app.getString(R.string.auth_error_password_short))
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            val result = when (state.mode) {
                AuthMode.Login -> authRepository.login(user, pass)
                AuthMode.Register -> authRepository.register(user, pass)
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage =
                            e.message?.takeIf { it.isNotBlank() }
                                ?: app.getString(R.string.auth_error_generic),
                    )
                },
            )
        }
    }
}
