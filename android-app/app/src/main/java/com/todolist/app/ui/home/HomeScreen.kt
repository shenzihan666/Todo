package com.todolist.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeContent(
    speechViewModel: SpeechViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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

    Column(
        modifier =
            modifier
                .fillMaxSize()
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
            modifier = Modifier.padding(bottom = 16.dp),
        )
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
