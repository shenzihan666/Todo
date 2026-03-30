package com.todolist.app.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BillReadDto(
    val id: Int,
    val tenant_id: String,
    val title: String,
    val description: String? = null,
    val amount: Double,
    val type: String,
    val category: String? = null,
    val billed_at: String? = null,
    val created_at: String,
    val updated_at: String,
)
