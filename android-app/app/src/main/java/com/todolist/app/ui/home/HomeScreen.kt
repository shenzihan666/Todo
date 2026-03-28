package com.todolist.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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

    val pickImage =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                speechViewModel.onImagePicked(uri)
            }
        }

    val transcript by speechViewModel.transcript.collectAsStateWithLifecycle()
    val messages by speechViewModel.messages.collectAsStateWithLifecycle()
    val isListening by speechViewModel.isListening.collectAsStateWithLifecycle()
    val audioLevel by speechViewModel.audioLevel.collectAsStateWithLifecycle()
    val errorMessage by speechViewModel.errorMessage.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, transcript, errorMessage) {
        if (messages.isNotEmpty() || transcript.isNotEmpty() || errorMessage != null) {
            var lastIndex = messages.size - 1
            if (isListening && transcript.isNotEmpty()) {
                lastIndex += 1
            }
            if (errorMessage != null) {
                lastIndex += 1
            }
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    // Full-height list with mic overlaid at the bottom (transparent) so messages use the whole
    // area; bottom padding keeps the last bubble scrollable above the mic / + controls.
    val micOverlayBottomInset = 240.dp

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    bottom = micOverlayBottomInset,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
        }

        // Mic column centered horizontally; + sits just outside the 168dp ring, vertically aligned with the green mic circle
        // (not the column’s geometric center — timer text above shifts the mic center down).
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            VoiceMicButton(
                isListening = isListening,
                audioLevel = audioLevel,
                hasPermission = permissionGranted,
                onHoldStart = { speechViewModel.onHoldStart() },
                onHoldEnd = { speechViewModel.onHoldEnd() },
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.align(Alignment.Center),
            )
            ImageAddButton(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .offset(
                            // x: screen center → right edge of 168dp ring + half IconButton (~48dp)
                            x = 84.dp + 4.dp,
                            // y: align with center of 168dp mic area (below Column center due to timer row layout)
                            y = 20.dp,
                        ),
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
