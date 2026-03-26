package com.todolist.app.domain.speech

import kotlinx.coroutines.flow.StateFlow

enum class TranscriberState {
    Idle,
    Connecting,
    Listening,
    Processing,
    Error,
}

/**
 * Pluggable speech-to-text. Implementations may use WebSocket + server STT or local APIs.
 */
interface SpeechTranscriber {
    val state: StateFlow<TranscriberState>
    val transcript: StateFlow<String>
    val audioLevel: StateFlow<Float>

    /** [serverUrl] must be a full WebSocket URL (e.g. `ws://host:8000/api/v1/speech/ws`). */
    suspend fun startSession(serverUrl: String, language: String = "zh")

    suspend fun stopSession()

    fun destroy()
}
