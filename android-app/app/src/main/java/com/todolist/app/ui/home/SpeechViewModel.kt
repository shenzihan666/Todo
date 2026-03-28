package com.todolist.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.R
import com.todolist.app.data.network.AgentSseClient
import com.todolist.app.data.network.AgentSseEvent
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.data.repository.MediaRepositoryImpl
import com.todolist.app.domain.speech.SpeechTranscriber
import com.todolist.app.domain.speech.TranscriberState
import com.todolist.app.ui.settings.buildServerBaseUrl
import com.todolist.app.ui.settings.buildSpeechWebSocketUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val mediaRepository: MediaRepositoryImpl,
    private val agentSseClient: AgentSseClient,
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

    val isProcessing: StateFlow<Boolean> =
        transcriber.state
            .map { it == TranscriberState.Processing }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var agentJob: Job? = null

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
                        _messages.value =
                            _messages.value +
                            ChatMessage(
                                text = finalTranscript,
                                isUser = true,
                                isPending = false,
                            )
                        val assistantId = UUID.randomUUID().toString()
                        _messages.value =
                            _messages.value +
                            ChatMessage(
                                id = assistantId,
                                text = "",
                                isUser = false,
                                isPending = true,
                            )
                        agentJob?.cancel()
                        agentJob =
                            viewModelScope.launch {
                                try {
                                    runAgentChat(finalTranscript, assistantId)
                                } catch (e: CancellationException) {
                                    markAssistantFinished(assistantId)
                                    throw e
                                }
                            }
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
                        val accessToken = userPreferences.getCachedAccessToken().trim()
                        transcriber.startSession(
                            buildSpeechWebSocketUrl(
                                ip,
                                accessToken = accessToken.takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                    SessionMsg.Stop -> transcriber.stopSession()
                }
            }
        }
    }

    fun onHoldStart() {
        agentJob?.cancel()
        sessionControl.trySend(SessionMsg.Start)
    }

    fun onHoldEnd() {
        sessionControl.trySend(SessionMsg.Stop)
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val ip = userPreferences.serverIp.first()
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
            _errorMessage.value = null
            mediaRepository
                .uploadImage(
                    buildServerBaseUrl(ip),
                    uri,
                    getApplication(),
                )
                .fold(
                    onSuccess = { response ->
                        val app = getApplication<Application>()
                        _messages.value =
                            _messages.value +
                            ChatMessage(
                                text = app.getString(
                                    R.string.image_upload_user_message,
                                    response.originalFilename,
                                ),
                                isUser = true,
                            )
                        _messages.value =
                            _messages.value +
                            ChatMessage(
                                text = app.getString(
                                    R.string.image_upload_mock_reply,
                                    response.originalFilename,
                                ),
                                isUser = false,
                            )
                    },
                    onFailure = { e ->
                        _errorMessage.value =
                            e.message?.takeIf { it.isNotBlank() }
                                ?: getApplication<Application>().getString(R.string.image_upload_error)
                    },
                )
        }
    }

    private fun markAssistantFinished(assistantId: String) {
        _messages.value =
            _messages.value.map { m ->
                if (m.id == assistantId) {
                    m.copy(isPending = false)
                } else {
                    m
                }
            }
    }

    private suspend fun runAgentChat(
        userText: String,
        assistantId: String,
    ) {
        val ip = userPreferences.serverIp.first()
        if (ip.isEmpty()) {
            _errorMessage.value =
                getApplication<Application>().getString(R.string.voice_error_no_server)
            markAssistantFinished(assistantId)
            return
        }
        if (userPreferences.getCachedAccessToken().trim().isEmpty()) {
            _errorMessage.value =
                getApplication<Application>().getString(R.string.image_upload_error_not_signed_in)
            markAssistantFinished(assistantId)
            return
        }
        _errorMessage.value = null
        val base = buildServerBaseUrl(ip)
        try {
            try {
                agentSseClient.streamChat(base, userText).collect { ev ->
                    when (ev) {
                        is AgentSseEvent.Token -> {
                            _messages.value =
                                _messages.value.map { m ->
                                    if (m.id == assistantId) {
                                        m.copy(text = m.text + ev.text)
                                    } else {
                                        m
                                    }
                                }
                        }
                        is AgentSseEvent.Done -> {
                            markAssistantFinished(assistantId)
                        }
                        is AgentSseEvent.Error -> {
                            _errorMessage.value = ev.message
                            _messages.value =
                                _messages.value.map { m ->
                                    if (m.id == assistantId) {
                                        m.copy(
                                            isPending = false,
                                            text = m.text.ifEmpty { ev.message },
                                        )
                                    } else {
                                        m
                                    }
                                }
                        }
                        is AgentSseEvent.ToolCall -> {}
                        is AgentSseEvent.ToolResult -> {}
                    }
                }
            } finally {
                markAssistantFinished(assistantId)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _errorMessage.value =
                e.message?.takeIf { it.isNotBlank() }
                    ?: getApplication<Application>().getString(R.string.agent_chat_error)
            _messages.value =
                _messages.value.map { m ->
                    if (m.id == assistantId) {
                        m.copy(
                            isPending = false,
                            text = m.text.ifEmpty { _errorMessage.value ?: "" },
                        )
                    } else {
                        m
                    }
                }
        }
    }

    private sealed interface SessionMsg {
        data object Start : SessionMsg
        data object Stop : SessionMsg
    }

    override fun onCleared() {
        super.onCleared()
        agentJob?.cancel()
        transcriber.destroy()
    }
}
