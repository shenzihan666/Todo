package com.todolist.app.ui.bills

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.todolist.app.R
import com.todolist.app.data.preferences.UserPreferencesRepository
import com.todolist.app.data.repository.BillRepositoryImpl
import com.todolist.app.ui.settings.buildServerBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BillsViewModel(
    application: Application,
    private val billRepository: BillRepositoryImpl,
    private val userPreferences: UserPreferencesRepository,
) : AndroidViewModel(application) {

    private val _rows = MutableStateFlow<List<BillListRow>>(emptyList())
    val rows: StateFlow<List<BillListRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val ip = userPreferences.serverIp.first().trim()
            if (ip.isEmpty()) {
                _errorMessage.value =
                    getApplication<Application>().getString(R.string.voice_error_no_server)
                return@launch
            }
            if (userPreferences.getCachedAccessToken().trim().isEmpty()) {
                _errorMessage.value =
                    getApplication<Application>().getString(R.string.image_upload_error_not_signed_in)
                return@launch
            }
            _isLoading.value = true
            _errorMessage.value = null
            val base = buildServerBaseUrl(ip)
            val app = getApplication<Application>()
            val income = app.getString(R.string.bill_type_income)
            val expense = app.getString(R.string.bill_type_expense)
            billRepository.listBills(base).fold(
                onSuccess = { list ->
                    _rows.value = list.map { it.toBillListRow(income, expense) }
                },
                onFailure = { e ->
                    _errorMessage.value =
                        e.message?.takeIf { it.isNotBlank() }
                            ?: app.getString(R.string.bills_load_failed)
                },
            )
            _isLoading.value = false
        }
    }
}
