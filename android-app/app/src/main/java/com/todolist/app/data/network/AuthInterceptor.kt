package com.todolist.app.data.network

import com.todolist.app.data.preferences.UserPreferencesRepository
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val prefs: UserPreferencesRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }

        val token = prefs.getCachedAccessToken()
        if (token.isEmpty()) {
            return chain.proceed(request)
        }

        val authedRequest = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authedRequest)
    }
}
