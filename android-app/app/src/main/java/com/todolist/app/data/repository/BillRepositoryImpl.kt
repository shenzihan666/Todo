package com.todolist.app.data.repository

import com.todolist.app.data.network.ApiService
import com.todolist.app.data.network.dto.BillReadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BillRepositoryImpl(
    private val apiServiceFactory: (String) -> ApiService,
) {
    suspend fun listBills(
        baseUrl: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<BillReadDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                apiServiceFactory(baseUrl).listBills(limit = limit, offset = offset)
            }
        }
}
