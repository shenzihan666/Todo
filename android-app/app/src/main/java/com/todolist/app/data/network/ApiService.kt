package com.todolist.app.data.network

import com.todolist.app.data.network.dto.AuthResponse
import com.todolist.app.data.network.dto.HealthResponse
import com.todolist.app.data.network.dto.LoginRequest
import com.todolist.app.data.network.dto.MediaUploadResponse
import com.todolist.app.data.network.dto.RefreshRequest
import com.todolist.app.data.network.dto.RegisterRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @GET("api/v1/health")
    suspend fun getHealth(): HealthResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequest)

    @Multipart
    @POST("api/v1/media")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): MediaUploadResponse
}
