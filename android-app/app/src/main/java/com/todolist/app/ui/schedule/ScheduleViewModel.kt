package com.todolist.app.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.R
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.data.repository.TodoRepositoryImpl
import com.todolist.app.ui.settings.buildServerBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleViewModel(
    application: Application,
    private val todoRepository: TodoRepositoryImpl,
    private val userPreferences: UserPreferencesRepository,
) : AndroidViewModel(application) {

    private val _events = MutableStateFlow<List<ScheduleEvent>>(emptyList())
    val events: StateFlow<List<ScheduleEvent>> = _events.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val ip = userPreferences.serverIp.first().trim()
            if (ip.isEmpty()) {
                _errorMessage.value =
                    getApplication<Application>().getString(R.string.voice_error_no_server)
                return@launch
            }
            if (userPreferences.getCachedAccessToken().trim().isEmpty()) {
                _errorMessage.value =
                    getApplication<Application>().getString(R.string.image_upload_error_not_signed_in)
                return@launch
            }
            _isLoading.value = true
            _errorMessage.value = null
            val base = buildServerBaseUrl(ip)
            todoRepository.listTodos(base).fold(
                onSuccess = { list ->
                    _events.value = list.map { it.toScheduleEvent() }
                },
                onFailure = { e ->
                    _errorMessage.value =
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getApplication<Application>().getString(R.string.schedule_todos_load_failed)
                },
            )
            _isLoading.value = false
        }
    }
}
