package com.todolist.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.todolist.app.di.AppContainer
import com.todolist.app.ui.health.HealthViewModel

class TodoListApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }

    fun healthViewModelFactory(): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HealthViewModel::class.java)) {
                    return HealthViewModel(container.healthRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel: $modelClass")
            }
        }
}
