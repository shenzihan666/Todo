package com.todolist.app.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaUploadResponse(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("created_at") val createdAt: String,
)
