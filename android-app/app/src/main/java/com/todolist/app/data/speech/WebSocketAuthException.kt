package com.todolist.app.data.speech

/** WebSocket HTTP upgrade rejected with 401/403 (e.g. expired JWT). */
class WebSocketAuthException(
    val httpCode: Int,
    cause: Throwable?,
) : Exception("WebSocket handshake failed: HTTP $httpCode", cause)
