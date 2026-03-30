package com.todolist.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.todolist.app.R
import com.todolist.app.TodoListApplication
import com.todolist.app.i18n.AppLocale
import com.todolist.app.domain.model.HealthFailureReason
import com.todolist.app.ui.components.TodoListAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    showNavigationBack: Boolean = true,
) {
    val factory = (LocalContext.current.applicationContext as TodoListApplication).settingsViewModelFactory()
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val draftIp by viewModel.draftServerIp.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()
    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    val appLocale by viewModel.appLocale.collectAsStateWithLifecycle()

    SettingsScreen(
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        onLogout = { viewModel.logout(onLoggedOut) },
        currentUsername = currentUsername,
        selectedAppLocale = appLocale,
        onAppLocaleChange = viewModel::setAppLocale,
        serverIp = draftIp,
        onServerIpChange = viewModel::onServerIpChange,
        onTestConnection = viewModel::testConnection,
        connectionState = connectionState,
        validationError = validationError,
        showNavigationBack = showNavigationBack,
    )
}

@Composable
private fun healthFailureMessage(reason: HealthFailureReason): String = when (reason) {
    is HealthFailureReason.BadServerStatus ->
        stringResource(R.string.health_error_bad_status, reason.status)
    HealthFailureReason.CannotConnect -> stringResource(R.string.health_error_cannot_connect)
    HealthFailureReason.Timeout -> stringResource(R.string.health_error_timeout)
    is HealthFailureReason.UnknownHost -> {
        val h = reason.host.trim()
        if (h.isEmpty()) {
            stringResource(R.string.health_error_unknown_host_generic)
        } else {
            stringResource(R.string.health_error_unknown_host, h)
        }
    }
    is HealthFailureReason.Generic ->
        reason.message?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.health_error_unknown)
}

private val languageRadioOptions: List<Pair<AppLocale, Int>> =
    listOf(
        AppLocale.SYSTEM to R.string.language_system,
        AppLocale.EN to R.string.language_english,
        AppLocale.ZH_CN to R.string.language_chinese_simplified,
    )

@Composable
private fun LanguageSelection(
    selected: AppLocale,
    onSelect: (AppLocale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup()) {
        languageRadioOptions.forEach { (locale, labelRes) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = selected == locale,
                        onClick = { onSelect(locale) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                RadioButton(
                    selected = selected == locale,
                    onClick = null,
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsFormContent(
    selectedAppLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit,
    serverIp: String,
    onServerIpChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    connectionState: ConnectionUiState,
    validationError: ServerSettingsError?,
    currentUsername: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testLabel = stringResource(R.string.test_connection)
    val testDesc = stringResource(R.string.content_desc_test_connection)
    val logoutDesc = stringResource(R.string.content_desc_logout)

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_section_language),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LanguageSelection(
            selected = selectedAppLocale,
            onSelect = onAppLocaleChange,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.settings_section_server),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        OutlinedTextField(
            value = serverIp,
            onValueChange = onServerIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.server_ip_label)) },
            placeholder = { Text(stringResource(R.string.server_ip_hint)) },
            supportingText = {
                if (validationError == ServerSettingsError.EmptyServerIp) {
                    Text(
                        text = stringResource(R.string.error_server_ip_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(stringResource(R.string.server_port_note))
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = validationError != null,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onTestConnection,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = testDesc },
        ) {
            Text(testLabel)
        }

        Spacer(modifier = Modifier.height(24.dp))

        val statusText = when (connectionState) {
            ConnectionUiState.Idle -> stringResource(R.string.status_idle_hint)
            ConnectionUiState.Checking -> stringResource(R.string.status_checking)
            ConnectionUiState.Connected -> stringResource(R.string.status_connected)
            is ConnectionUiState.Failed -> stringResource(
                R.string.status_failed,
                healthFailureMessage(connectionState.reason),
            )
        }
        val statusColor = when (connectionState) {
            ConnectionUiState.Connected -> MaterialTheme.colorScheme.primary
            is ConnectionUiState.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = statusColor,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.settings_section_account),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (currentUsername.isNotBlank()) {
            Text(
                text = stringResource(R.string.settings_signed_in_as, currentUsername),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = logoutDesc },
        ) {
            Text(stringResource(R.string.logout))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    currentUsername: String,
    selectedAppLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit,
    serverIp: String,
    onServerIpChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    connectionState: ConnectionUiState,
    validationError: ServerSettingsError?,
    modifier: Modifier = Modifier,
    showNavigationBack: Boolean = true,
) {
    val title = stringResource(R.string.settings)
    val backDesc = stringResource(R.string.content_desc_navigate_back)

    if (showNavigationBack) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TodoListAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.semantics { contentDescription = backDesc },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            SettingsFormContent(
                selectedAppLocale = selectedAppLocale,
                onAppLocaleChange = onAppLocaleChange,
                serverIp = serverIp,
                onServerIpChange = onServerIpChange,
                onTestConnection = onTestConnection,
                connectionState = connectionState,
                validationError = validationError,
                currentUsername = currentUsername,
                onLogout = onLogout,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    } else {
        SettingsFormContent(
            selectedAppLocale = selectedAppLocale,
            onAppLocaleChange = onAppLocaleChange,
            serverIp = serverIp,
            onServerIpChange = onServerIpChange,
            onTestConnection = onTestConnection,
            connectionState = connectionState,
            validationError = validationError,
            currentUsername = currentUsername,
            onLogout = onLogout,
            modifier = modifier.fillMaxSize(),
        )
    }
}
