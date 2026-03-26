package com.todolist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.todolist.app.ui.theme.TodoListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoListTheme {
                val vm: ConnectionViewModel = viewModel()
                LaunchedEffect(Unit) { vm.refresh() }
                val state by vm.state.collectAsStateWithLifecycle()
                ConnectionScreen(
                    state = state,
                    onRefresh = { vm.refresh() },
                )
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    state: ConnectionUiState,
    onRefresh: () -> Unit,
) {
    val statusText = when (state) {
        ConnectionUiState.Checking -> stringResource(R.string.status_checking)
        ConnectionUiState.Connected -> stringResource(R.string.status_connected)
        ConnectionUiState.Offline -> stringResource(R.string.status_offline)
    }
    val refreshLabel = stringResource(R.string.refresh)
    val refreshDesc = stringResource(R.string.content_desc_refresh)

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRefresh,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                    )
                },
                text = { Text(refreshLabel) },
                modifier = Modifier.semantics { contentDescription = refreshDesc },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
