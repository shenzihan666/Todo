package com.todolist.app.data.network

import com.todolist.app.data.network.dto.HealthResponse
import retrofit2.http.GET

interface ApiService {

    @GET("api/v1/health")
    suspend fun getHealth(): HealthResponse
}
