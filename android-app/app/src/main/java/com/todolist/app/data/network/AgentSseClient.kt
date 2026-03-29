package com.todolist.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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

    data class ProposedActions(val actions: List<ProposedAction>) : AgentSseEvent()

    data object Done : AgentSseEvent()

    data class Error(val message: String) : AgentSseEvent()
}

@Serializable
data class ProposedAction(
    val action: String,
    val args: JsonObject = JsonObject(emptyMap()),
    @SerialName("display_title") val displayTitle: String,
    @SerialName("display_scheduled_at") val displayScheduledAt: String? = null,
)

@Serializable
data class ExecuteActionsResponseBody(
    val executed: Int,
    val results: List<String>,
)

@Serializable
private data class AgentChatRequestBody(
    val message: String,
    val media_ids: List<String> = emptyList(),
    val require_confirmation: Boolean = false,
)

@Serializable
private data class ExecuteActionsRequestBody(
    val actions: List<ProposedAction>,
)

class AgentSseClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    fun streamChat(
        baseUrl: String,
        message: String,
        requireConfirmation: Boolean = true,
    ): Flow<AgentSseEvent> =
        flow {
            val url = baseUrl.trim().trimEnd('/') + "/api/v1/agent/chat"
            val bodyJson =
                json.encodeToString(
                    AgentChatRequestBody.serializer(),
                    AgentChatRequestBody(
                        message = message,
                        require_confirmation = requireConfirmation,
                    ),
                )
            val request =
                Request.Builder()
                    .url(url)
                    .addHeader("Accept", "text/event-stream")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(AgentSseEvent.Error("HTTP ${response.code}"))
                return@flow
            }
            val source =
                response.body?.source() ?: run {
                    emit(AgentSseEvent.Error("Empty response body"))
                    return@flow
                }
            try {
                consumeSse(this, source)
            } finally {
                response.close()
            }
        }.flowOn(Dispatchers.IO)

    suspend fun executeActions(
        baseUrl: String,
        actions: List<ProposedAction>,
    ): Result<ExecuteActionsResponseBody> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trim().trimEnd('/') + "/api/v1/agent/execute-actions"
            val bodyJson =
                json.encodeToString(
                    ExecuteActionsRequestBody.serializer(),
                    ExecuteActionsRequestBody(actions = actions),
                )
            val request =
                Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
            runCatching {
                val response = okHttpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $bodyStr")
                }
                json.decodeFromString(ExecuteActionsResponseBody.serializer(), bodyStr)
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
                "proposed_actions" -> parseProposedActions(out, data)
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

    private suspend fun parseProposedActions(
        out: FlowCollector<AgentSseEvent>,
        raw: String,
    ) {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val arr = obj["actions"]?.jsonArray ?: return
        val actions =
            arr.mapNotNull { el ->
                runCatching {
                    json.decodeFromJsonElement(ProposedAction.serializer(), el)
                }.getOrNull()
            }
        if (actions.isNotEmpty()) {
            out.emit(AgentSseEvent.ProposedActions(actions))
        }
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
