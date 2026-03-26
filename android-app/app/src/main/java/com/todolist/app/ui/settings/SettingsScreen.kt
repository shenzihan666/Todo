package com.todolist.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = (LocalContext.current.applicationContext as TodoListApplication).settingsViewModelFactory()
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val draftIp by viewModel.draftServerIp.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()

    SettingsScreen(
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        serverIp = draftIp,
        onServerIpChange = viewModel::onServerIpChange,
        onTestConnection = viewModel::testConnection,
        connectionState = connectionState,
        validationError = validationError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    serverIp: String,
    onServerIpChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    connectionState: ConnectionUiState,
    validationError: ServerSettingsError?,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.settings)
    val backDesc = stringResource(R.string.content_desc_navigate_back)
    val testLabel = stringResource(R.string.test_connection)
    val testDesc = stringResource(R.string.content_desc_test_connection)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
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
                ConnectionUiState.Offline -> stringResource(R.string.status_offline)
            }
            val statusStyle = when (connectionState) {
                ConnectionUiState.Connected -> MaterialTheme.colorScheme.primary
                ConnectionUiState.Offline -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = statusStyle,
            )
        }
    }
}
