package com.todolist.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.R
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.domain.speech.SpeechTranscriber
import com.todolist.app.domain.speech.TranscriberState
import com.todolist.app.ui.settings.buildSpeechWebSocketUrl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isPending: Boolean = false,
)

class SpeechViewModel(
    application: Application,
    private val transcriber: SpeechTranscriber,
    private val userPreferences: UserPreferencesRepository,
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

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

    /**
     * Single consumer so [onHoldStart] / [onHoldEnd] stay in finger-down/up order.
     * Otherwise [stopSession] can run while preferences are still loading (state [Idle]),
     * return early, and a late [startSession] leaves the UI stuck "listening".
     */
    private val sessionControl = Channel<SessionMsg>(capacity = Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            transcriber.state.collect { state ->
                if (state == TranscriberState.Idle) {
                    val finalTranscript = transcriber.transcript.value
                    if (finalTranscript.isNotBlank()) {
                        _messages.value = _messages.value + ChatMessage(
                            text = finalTranscript,
                            isUser = true,
                            isPending = false,
                        )
                        // Mock AI reply
                        _messages.value = _messages.value + ChatMessage(
                            text = "收到你的语音：$finalTranscript",
                            isUser = false,
                            isPending = false,
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            for (msg in sessionControl) {
                when (msg) {
                    SessionMsg.Start -> {
                        val ip = userPreferences.serverIp.first()
                        if (ip.isEmpty()) {
                            _errorMessage.value =
                                getApplication<Application>().getString(R.string.voice_error_no_server)
                            continue
                        }
                        _errorMessage.value = null
                        transcriber.startSession(buildSpeechWebSocketUrl(ip))
                    }
                    SessionMsg.Stop -> transcriber.stopSession()
                }
            }
        }
    }

    fun onHoldStart() {
        sessionControl.trySend(SessionMsg.Start)
    }

    fun onHoldEnd() {
        sessionControl.trySend(SessionMsg.Stop)
    }

    private sealed interface SessionMsg {
        data object Start : SessionMsg
        data object Stop : SessionMsg
    }

    override fun onCleared() {
        super.onCleared()
        transcriber.destroy()
    }
}
