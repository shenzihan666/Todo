package com.todolist.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

sealed class AgentSseEvent {
    data class Token(val text: String) : AgentSseEvent()

    data class ToolCall(val tool: String) : AgentSseEvent()

    data class ToolResult(val tool: String, val content: String) : AgentSseEvent()

    data object Done : AgentSseEvent()

    data class Error(val message: String) : AgentSseEvent()
}

@Serializable
private data class AgentChatRequestBody(
    val message: String,
    val media_ids: List<String> = emptyList(),
)

class AgentSseClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    fun streamChat(
        baseUrl: String,
        message: String,
    ): Flow<AgentSseEvent> =
        flow {
            val out = this
            withContext(Dispatchers.IO) {
                val url = baseUrl.trim().trimEnd('/') + "/api/v1/agent/chat"
                val bodyJson =
                    json.encodeToString(
                        AgentChatRequestBody.serializer(),
                        AgentChatRequestBody(message = message),
                    )
                val request =
                    Request.Builder()
                        .url(url)
                        .addHeader("Accept", "text/event-stream")
                        .post(bodyJson.toRequestBody("application/json".toMediaType()))
                        .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    out.emit(AgentSseEvent.Error("HTTP ${response.code}"))
                    return@withContext
                }
                val source =
                    response.body?.source() ?: run {
                        out.emit(AgentSseEvent.Error("Empty response body"))
                        return@withContext
                    }
                try {
                    consumeSse(out, source)
                } finally {
                    response.close()
                }
            }
        }

    private suspend fun consumeSse(
        out: FlowCollector<AgentSseEvent>,
        source: BufferedSource,
    ) {
        var eventName: String? = null
        val dataBuilder = StringBuilder()

        suspend fun flush() {
            val name = eventName ?: return
            val data = dataBuilder.toString()
            dataBuilder.clear()
            eventName = null
            when (name) {
                "token" -> out.emit(AgentSseEvent.Token(parseTokenPayload(data)))
                "tool_call" -> {
                    val tool = parseToolField(data, "tool")
                    out.emit(AgentSseEvent.ToolCall(tool))
                }
                "tool_result" -> {
                    val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                    val tool = obj?.get("tool")?.jsonPrimitive?.contentOrNull ?: ""
                    val content = obj?.get("content")?.jsonPrimitive?.contentOrNull ?: data
                    out.emit(AgentSseEvent.ToolResult(tool, content))
                }
                "done" -> out.emit(AgentSseEvent.Done)
                "error" -> out.emit(AgentSseEvent.Error(parseErrorPayload(data)))
                else -> {}
            }
        }

        while (true) {
            val line = source.readUtf8Line() ?: break
            when {
                line.startsWith("event:") -> {
                    flush()
                    eventName = line.substring(6).trim()
                }
                line.startsWith("data:") -> {
                    if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
                    dataBuilder.append(line.substring(5).trimStart())
                }
                line.isEmpty() -> flush()
            }
        }
        flush()
    }

    private fun parseTokenPayload(raw: String): String {
        if (raw.isEmpty()) return ""
        return try {
            when (val el = json.parseToJsonElement(raw)) {
                is JsonPrimitive -> el.contentOrNull ?: raw
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun parseToolField(
        raw: String,
        key: String,
    ): String {
        return try {
            json.parseToJsonElement(raw).jsonObject[key]?.jsonPrimitive?.contentOrNull ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseErrorPayload(raw: String): String {
        if (raw.isEmpty()) return "Unknown error"
        return try {
            when (val el = json.parseToJsonElement(raw)) {
                is JsonPrimitive -> el.contentOrNull ?: raw
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }
}
