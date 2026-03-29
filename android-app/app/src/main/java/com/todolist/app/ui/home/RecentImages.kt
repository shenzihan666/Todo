package com.todolist.app.ui.home

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object RecentImages {
    /**
     * Recent images from [MediaStore], newest first. Requires read permission on API levels
     * where querying external images is restricted.
     */
    fun loadRecentUris(context: Context, limit: Int = 120): List<Uri> {
        val resolver = context.contentResolver
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val mimeSelection =
            "${MediaStore.Images.Media.MIME_TYPE} IN (?,?,?,?)"
        val mimeArgs =
            arrayOf(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/gif",
            )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val uris = mutableListOf<Uri>()
        resolver.query(collection, projection, mimeSelection, mimeArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idCol)
                uris.add(ContentUris.withAppendedId(collection, id))
                count++
            }
        }
        return uris
    }
}
