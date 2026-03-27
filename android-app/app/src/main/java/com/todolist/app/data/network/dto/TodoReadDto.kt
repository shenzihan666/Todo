package com.todolist.app.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TodoReadDto(
    val id: Int,
    val tenant_id: String,
    val title: String,
    val description: String? = null,
    val completed: Boolean,
    val created_at: String,
    val updated_at: String,
)
