package com.todolist.app.data.repository

import com.todolist.app.data.network.ApiService
import com.todolist.app.data.network.dto.TodoReadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TodoRepositoryImpl(
    private val apiServiceFactory: (String) -> ApiService,
) {
    suspend fun listTodos(
        baseUrl: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<List<TodoReadDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                apiServiceFactory(baseUrl).listTodos(limit = limit, offset = offset)
            }
        }
}
