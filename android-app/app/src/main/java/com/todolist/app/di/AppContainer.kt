package com.todolist.app.di

import android.content.Context
import com.todolist.app.BuildConfig
import com.todolist.app.data.network.AgentSseClient
import com.todolist.app.data.network.ApiService
import com.todolist.app.data.network.AuthInterceptor
import com.todolist.app.data.network.TokenAuthenticator
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.data.repository.AuthRepository
import com.todolist.app.data.repository.HealthRepositoryImpl
import com.todolist.app.data.repository.MediaRepositoryImpl
import com.todolist.app.data.repository.TodoRepositoryImpl
import com.todolist.app.data.speech.RemoteSpeechTranscriber
import com.todolist.app.data.speech.SpeechTokenProviderImpl
import com.todolist.app.domain.repository.HealthRepository
import com.todolist.app.domain.speech.SpeechTranscriber
import com.todolist.app.ui.settings.buildServerBaseUrl
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Application-scoped dependency graph (manual DI; swap for Hilt/KSP when plugin resolves).
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    private val speechJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /** Logging only — speech WebSocket and token refresh (no auth loop). */
    private val plainOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val authenticatedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor(userPreferencesRepository))
            .authenticator(
                TokenAuthenticator(
                    getBaseUrl = {
                        val ip = userPreferencesRepository.getCachedServerIp().trim()
                        if (ip.isEmpty()) {
                            ""
                        } else {
                            buildServerBaseUrl(ip)
                        }
                    },
                    getRefreshToken = { userPreferencesRepository.getCachedRefreshToken() },
                    onTokenRefreshed = { access, refresh ->
                        userPreferencesRepository.updateTokens(access, refresh)
                    },
                    onRefreshFailed = { userPreferencesRepository.clearAuth() },
                    json = json,
                    plainClient = plainOkHttpClient,
                ),
            )
            .build()
    }

    /** Same auth as [authenticatedOkHttpClient] but tolerates long-running SSE streams. */
    private val authenticatedAgentOkHttpClient: OkHttpClient by lazy {
        authenticatedOkHttpClient.newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    private val speechTokenProvider: SpeechTokenProviderImpl by lazy {
        SpeechTokenProviderImpl(
            prefs = userPreferencesRepository,
            json = json,
            plainClient = plainOkHttpClient,
        )
    }

    fun createSpeechTranscriber(): SpeechTranscriber =
        RemoteSpeechTranscriber(
            RemoteSpeechTranscriber.defaultWsClient(plainOkHttpClient),
            speechJson,
            speechTokenProvider,
        )

    fun createAgentSseClient(): AgentSseClient =
        AgentSseClient(authenticatedAgentOkHttpClient, json)

    fun createApiService(baseUrl: String): ApiService {
        val base = baseUrl.trim().trimEnd('/') + "/"
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(authenticatedOkHttpClient)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
            .create(ApiService::class.java)
    }

    val healthRepository: HealthRepository by lazy {
        HealthRepositoryImpl(::createApiService)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(userPreferencesRepository, ::createApiService)
    }

    val mediaRepository: MediaRepositoryImpl by lazy {
        MediaRepositoryImpl(::createApiService)
    }

    val todoRepository: TodoRepositoryImpl by lazy {
        TodoRepositoryImpl(::createApiService)
    }
}
