package com.todolist.app.domain.speech

/**
 * Supplies access tokens and refresh for speech WebSocket (OkHttp does not run [Authenticator] on WS upgrade).
 */
interface SpeechTokenProvider {
    fun getAccessToken(): String

    /** Calls `/auth/refresh`, persists new tokens on success; returns new access token or null. */
    suspend fun refreshAccessToken(): String?

    /** Refresh failed or repeated auth failure — e.g. clear session and return user to login. */
    suspend fun onRefreshFailed()
}
