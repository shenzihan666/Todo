package com.todolist.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.todolist.app.R
import com.todolist.app.TodoListApplication
import com.todolist.app.ui.components.TodoListAppBar

@Composable
fun HomeRoute(
    onNavigateToSettings: () -> Unit,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as TodoListApplication
    val prefs = app.container.userPreferencesRepository

    LaunchedEffect(prefs) {
        prefs.isLoggedIn.collect { loggedIn ->
            if (!loggedIn) {
                onNavigateToAuth()
            }
        }
    }

    val speechViewModel: SpeechViewModel = viewModel(factory = app.speechViewModelFactory())
    HomeScreen(
        modifier = modifier,
        onNavigateToSettings = onNavigateToSettings,
        speechViewModel = speechViewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    speechViewModel: SpeechViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeTitle = stringResource(R.string.home_title)
    val settingsDesc = stringResource(R.string.content_desc_open_settings)
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            permissionGranted = granted
        }

    val transcript by speechViewModel.transcript.collectAsStateWithLifecycle()
    val messages by speechViewModel.messages.collectAsStateWithLifecycle()
    val isListening by speechViewModel.isListening.collectAsStateWithLifecycle()
    val audioLevel by speechViewModel.audioLevel.collectAsStateWithLifecycle()
    val errorMessage by speechViewModel.errorMessage.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, transcript) {
        if (messages.isNotEmpty() || transcript.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    var gearRotationTarget by remember { mutableFloatStateOf(0f) }
    val gearRotation by animateFloatAsState(
        targetValue = gearRotationTarget,
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
        label = "settings_gear_rotation",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TodoListAppBar(
                title = homeTitle,
                actions = {
                    IconButton(
                        onClick = {
                            gearRotationTarget += 360f
                            onNavigateToSettings()
                        },
                        modifier = Modifier.semantics { contentDescription = settingsDesc },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.rotate(gearRotation),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.padding(top = 8.dp)) }

                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        text = msg.text,
                        isUser = msg.isUser,
                        isPending = msg.isPending,
                    )
                }

                if (isListening && transcript.isNotEmpty()) {
                    item {
                        ChatBubble(
                            text = transcript,
                            isUser = true,
                            isPending = true,
                        )
                    }
                }

                if (errorMessage != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.padding(bottom = 16.dp)) }
            }

            VoiceMicButton(
                isListening = isListening,
                audioLevel = audioLevel,
                hasPermission = permissionGranted,
                onHoldStart = { speechViewModel.onHoldStart() },
                onHoldEnd = { speechViewModel.onHoldEnd() },
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier =
                    Modifier
                        .padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
fun ChatBubble(
    text: String,
    isUser: Boolean,
    isPending: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val textColor =
        if (isUser) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val shape =
        if (isUser) {
            RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        } else {
            RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = if (isPending) backgroundColor.copy(alpha = 0.6f) else backgroundColor,
                        shape = shape,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (isPending) "$text..." else text,
                color = if (isPending) textColor.copy(alpha = 0.7f) else textColor,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
