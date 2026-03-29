package com.todolist.app.data.speech

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

@Serializable
data class ClientStreamConfig(
    val sample_rate: Int = 16_000,
    val encoding: String = "pcm_s16le",
    val channels: Int = 1,
    val language: String? = "zh",
)

@Serializable
data class SpeechStartPayload(
    val type: String = "start",
    val config: ClientStreamConfig = ClientStreamConfig(),
)

@Serializable
data class SpeechStopPayload(
    val type: String = "stop",
)

@Serializable
private data class PartialMessageDto(
    val type: String,
    val text: String,
)

@Serializable
data class SegmentDto(
    val start: Double,
    val end: Double,
    val text: String,
)

@Serializable
private data class FinalMessageDto(
    val type: String,
    val text: String,
    val segments: List<SegmentDto> = emptyList(),
    val language: String? = null,
)

@Serializable
private data class ErrorMessageDto(
    val type: String,
    val code: String,
    val message: String,
)

sealed interface ServerSpeechMessage {
    data class Partial(val text: String) : ServerSpeechMessage
    data class Final(
        val text: String,
        val segments: List<SegmentDto> = emptyList(),
        val language: String? = null,
    ) : ServerSpeechMessage

    data class Error(val code: String, val message: String) : ServerSpeechMessage
}

/**
 * OkHttp WebSocket client for `/api/v1/speech/ws` protocol.
 */
class WebSocketSpeechClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private var webSocket: WebSocket? = null
    private val incoming = Channel<ServerSpeechMessage>(capacity = Channel.BUFFERED)

    val messages: Flow<ServerSpeechMessage> = incoming.receiveAsFlow()

    /**
     * @param bearerToken If non-blank, sends `Authorization: Bearer` (RFC 6750). Base [wsUrl] should have no `access_token` query when using Bearer.
     */
    suspend fun connect(wsUrl: String, bearerToken: String? = null): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            val requestBuilder = Request.Builder().url(wsUrl)
            bearerToken?.trim()?.takeIf { it.isNotEmpty() }?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }
            val request = requestBuilder.build()
            val listener =
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        this@WebSocketSpeechClient.webSocket = webSocket
                        if (finished.compareAndSet(false, true)) {
                            cont.resume(Result.success(Unit))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parseServerMessage(text)?.let { incoming.trySend(it) }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val code = response?.code
                        val failure =
                            if (code == 401 || code == 403) {
                                WebSocketAuthException(code, t)
                            } else {
                                t
                            }
                        incoming.trySend(
                            ServerSpeechMessage.Error(
                                "ws_failure",
                                failure.message ?: "failure",
                            ),
                        )
                        if (finished.compareAndSet(false, true)) {
                            cont.resume(Result.failure(failure))
                        }
                    }
                }
            okHttpClient.newWebSocket(request, listener)
        }

    fun sendStart(config: ClientStreamConfig) {
        val payload = SpeechStartPayload(config = config)
        webSocket?.send(json.encodeToString(SpeechStartPayload.serializer(), payload))
    }

    fun sendPcmChunk(bytes: ByteArray) {
        webSocket?.send(bytes.toByteString())
    }

    fun sendStop() {
        val payload = SpeechStopPayload()
        webSocket?.send(json.encodeToString(SpeechStopPayload.serializer(), payload))
    }

    fun close() {
        webSocket?.close(1000, "done")
        webSocket = null
        incoming.close()
    }

    private fun parseServerMessage(text: String): ServerSpeechMessage? {
        val type =
            runCatching {
                json.decodeFromString(TypeOnly.serializer(), text).type
            }.getOrNull() ?: return null
        return when (type) {
            "partial" ->
                runCatching {
                    val p = json.decodeFromString(PartialMessageDto.serializer(), text)
                    ServerSpeechMessage.Partial(p.text)
                }.getOrNull()
            "final" ->
                runCatching {
                    val f = json.decodeFromString(FinalMessageDto.serializer(), text)
                    ServerSpeechMessage.Final(f.text, f.segments, f.language)
                }.getOrNull()
            "error" ->
                runCatching {
                    val e = json.decodeFromString(ErrorMessageDto.serializer(), text)
                    ServerSpeechMessage.Error(e.code, e.message)
                }.getOrNull()
            else -> null
        }
    }
}

@Serializable
private data class TypeOnly(
    val type: String,
)
