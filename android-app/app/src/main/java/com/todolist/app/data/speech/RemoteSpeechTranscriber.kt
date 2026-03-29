package com.todolist.app.data.speech

import com.todolist.app.data.audio.AudioRecorder
import com.todolist.app.domain.speech.SpeechTokenProvider
import com.todolist.app.domain.speech.SpeechTranscriber
import com.todolist.app.domain.speech.TranscriberState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RemoteSpeechTranscriber(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val tokenProvider: SpeechTokenProvider,
) : SpeechTranscriber {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val mutex = Mutex()
    private val recorder = AudioRecorder()

    private val _state = MutableStateFlow(TranscriberState.Idle)
    override val state: StateFlow<TranscriberState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow("")
    override val transcript: StateFlow<String> = _transcript.asStateFlow()

    override val audioLevel: StateFlow<Float> = recorder.audioLevel

    private var wsClient: WebSocketSpeechClient? = null
    private var chunkJob: Job? = null
    private var messageJob: Job? = null
    private var finalDeferred: CompletableDeferred<ServerSpeechMessage.Final>? = null

    /**
     * Opens WebSocket with Bearer token; on 401/403 refreshes once and retries (same as REST [TokenAuthenticator] flow).
     */
    private suspend fun openAuthenticatedWebSocket(serverUrl: String): WebSocketSpeechClient? {
        val first = WebSocketSpeechClient(okHttpClient, json)
        val t1 = tokenProvider.getAccessToken().trim()
        var result = first.connect(serverUrl, t1.takeIf { it.isNotEmpty() })
        if (result.isSuccess) return first

        if (result.exceptionOrNull() !is WebSocketAuthException) {
            return null
        }

        val newAccess = tokenProvider.refreshAccessToken()
        if (newAccess == null) {
            tokenProvider.onRefreshFailed()
            return null
        }

        val second = WebSocketSpeechClient(okHttpClient, json)
        result = second.connect(serverUrl, newAccess.trim().takeIf { it.isNotEmpty() })
        if (result.isSuccess) return second

        if (result.exceptionOrNull() is WebSocketAuthException) {
            tokenProvider.onRefreshFailed()
        }
        return null
    }

    override suspend fun startSession(serverUrl: String, language: String) {
        mutex.withLock {
            if (_state.value != TranscriberState.Idle) return
            _state.value = TranscriberState.Connecting
            _transcript.value = ""
            finalDeferred = null

            val client = openAuthenticatedWebSocket(serverUrl) ?: run {
                _state.value = TranscriberState.Error
                wsClient = null
                return
            }
            wsClient = client

            client.sendStart(
                ClientStreamConfig(
                    language = language.ifEmpty { null },
                ),
            )

            finalDeferred = CompletableDeferred()

            messageJob =
                scope.launch {
                    client.messages.collect { msg ->
                        when (msg) {
                            is ServerSpeechMessage.Partial -> {
                                _transcript.value = msg.text
                            }
                            is ServerSpeechMessage.Final -> {
                                _transcript.value = msg.text
                                finalDeferred?.let { d ->
                                    if (!d.isCompleted) {
                                        d.complete(msg)
                                    }
                                }
                            }
                            is ServerSpeechMessage.Error -> {
                                _transcript.value = msg.message
                                _state.value = TranscriberState.Error
                                finalDeferred?.let { d ->
                                    if (!d.isCompleted) {
                                        d.completeExceptionally(
                                            IllegalStateException("${msg.code}: ${msg.message}"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            recorder.start(scope)
            chunkJob =
                scope.launch {
                    recorder.chunks.collect { chunk ->
                        client.sendPcmChunk(chunk)
                    }
                }
            _state.value = TranscriberState.Listening
        }
    }

    override suspend fun stopSession() {
        mutex.withLock {
            if (_state.value == TranscriberState.Idle) return
            _state.value = TranscriberState.Processing
            recorder.stop()
            chunkJob?.cancel()
            chunkJob = null

            val client = wsClient ?: run {
                _state.value = TranscriberState.Idle
                return
            }
            client.sendStop()

            val fd = finalDeferred
            if (fd != null) {
                withTimeoutOrNull(15_000) {
                    runCatching { fd.await() }
                }
            }

            messageJob?.cancel()
            messageJob = null
            client.close()
            wsClient = null
            finalDeferred = null
            _state.value = TranscriberState.Idle
        }
    }

    override fun destroy() {
        job.cancel()
        recorder.stop()
        wsClient?.close()
        wsClient = null
    }

    companion object {
        /** OkHttp client suitable for long-lived WebSocket (no aggressive read timeout). */
        fun defaultWsClient(baseOkHttp: OkHttpClient): OkHttpClient =
            baseOkHttp.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
    }
}
