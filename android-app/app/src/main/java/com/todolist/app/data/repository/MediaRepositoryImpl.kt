package com.todolist.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.todolist.app.data.network.ApiService
import com.todolist.app.data.network.dto.MediaUploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MediaRepositoryImpl(
    private val apiServiceFactory: (String) -> ApiService,
) {
    suspend fun uploadImage(
        baseUrl: String,
        uri: Uri,
        context: Context,
    ): Result<MediaUploadResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val bytes =
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("cannot_read_uri")
                val fileName = displayName(resolver, uri) ?: "image.jpg"
                val part =
                    MultipartBody.Part.createFormData(
                        "file",
                        fileName,
                        bytes.toRequestBody(mime.toMediaTypeOrNull()),
                    )
                val api = apiServiceFactory(baseUrl)
                api.uploadMedia(part)
            }
        }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    return c.getString(idx)
                }
            }
        }
        return null
    }
}
