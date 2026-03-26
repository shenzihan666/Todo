package com.todolist.app.ui.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.R
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.domain.speech.SpeechTranscriber
import com.todolist.app.domain.speech.TranscriberState
import com.todolist.app.ui.settings.buildSpeechWebSocketUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeechViewModel(
    application: Application,
    private val transcriber: SpeechTranscriber,
    private val userPreferences: UserPreferencesRepository,
) : AndroidViewModel(application) {

    val transcript: StateFlow<String> = transcriber.transcript
    val audioLevel: StateFlow<Float> = transcriber.audioLevel

    val isListening: StateFlow<Boolean> =
        transcriber.state
            .map { s ->
                s == TranscriberState.Connecting || s == TranscriberState.Listening
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onHoldStart() {
        viewModelScope.launch {
            val ip = userPreferences.serverIp.first()
            if (ip.isEmpty()) {
                _errorMessage.value = getApplication<Application>().getString(R.string.voice_error_no_server)
                return@launch
            }
            _errorMessage.value = null
            val url = buildSpeechWebSocketUrl(ip)
            transcriber.startSession(url)
        }
    }

    fun onHoldEnd() {
        viewModelScope.launch {
            transcriber.stopSession()
        }
    }

    override fun onCleared() {
        super.onCleared()
        transcriber.destroy()
    }
}
