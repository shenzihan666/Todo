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

    /**
     * [serverUrl] is the base WebSocket URL without credentials, e.g. `ws://host:8000/api/v1/speech/ws`.
     * Implementation adds `Authorization: Bearer` using [SpeechTokenProvider] (and may refresh + retry once).
     */
    suspend fun startSession(serverUrl: String, language: String = "zh")

    suspend fun stopSession()

    /** Abort without final transcript; clears text and returns to [TranscriberState.Idle]. */
    suspend fun cancelSession()

    fun destroy()
}
