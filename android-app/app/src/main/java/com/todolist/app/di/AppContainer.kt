package com.todolist.app.di

import android.content.Context
import com.todolist.app.BuildConfig
import com.todolist.app.data.network.ApiService
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.data.repository.HealthRepositoryImpl
import com.todolist.app.domain.repository.HealthRepository
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
        isLenient = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        val base = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        val mediaType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
    }

    @Suppress("unused")
    private val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    val healthRepository: HealthRepository by lazy {
        HealthRepositoryImpl(okHttpClient, json)
    }
}
