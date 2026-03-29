package com.todolist.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.todolist.app.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun HomeContent(
    speechViewModel: SpeechViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
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

    var attachmentSheetVisible by remember { mutableStateOf(false) }

    var readImagesGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                readImagePermission(),
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val readImagesLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            readImagesGranted = granted
        }

    val pickImages =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
        ) { uris ->
            if (uris.isNotEmpty()) {
                speechViewModel.onImagesPicked(uris)
                attachmentSheetVisible = false
                focusManager.clearFocus()
            }
        }

    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            if (success && captureUri != null) {
                speechViewModel.onImagesPicked(listOf(captureUri!!))
                attachmentSheetVisible = false
                focusManager.clearFocus()
            }
        }

    fun launchCameraCapture() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        captureUri = uri
        takePicture.launch(uri)
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                launchCameraCapture()
            }
        }

    val transcript by speechViewModel.transcript.collectAsStateWithLifecycle()
    val messages by speechViewModel.messages.collectAsStateWithLifecycle()
    val isListening by speechViewModel.isListening.collectAsStateWithLifecycle()
    val isProcessing by speechViewModel.isProcessing.collectAsStateWithLifecycle()
    val audioLevel by speechViewModel.audioLevel.collectAsStateWithLifecycle()
    val errorMessage by speechViewModel.errorMessage.collectAsStateWithLifecycle()
    val pendingConfirmation by speechViewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val pendingImageUris by speechViewModel.pendingImageUris.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            speechViewModel.onHomeLeave()
        }
    }

    LaunchedEffect(attachmentSheetVisible) {
        if (attachmentSheetVisible) {
            readImagesGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    readImagePermission(),
                ) == PackageManager.PERMISSION_GRANTED
            if (!readImagesGranted) {
                readImagesLauncher.launch(readImagePermission())
            }
        }
    }

    // Full-height list with mic overlaid at the bottom (transparent) so messages use the whole
    // area; bottom padding keeps the last bubble scrollable above the mic / + controls.
    val pendingStripExtra = if (pendingImageUris.isNotEmpty()) 112.dp else 0.dp
    val micOverlayBottomInset =
        if (attachmentSheetVisible) {
            520.dp
        } else {
            240.dp + pendingStripExtra
        }

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
                val lastAssistantId = messages.lastOrNull { !it.isUser }?.id
                val isLatestAssistant = lastAssistantId != null && msg.id == lastAssistantId
                if (!msg.isUser && isLatestAssistant && msg.showAgentStatusRow) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ChatBubble(
                            text = msg.text,
                            isUser = false,
                            isPending = msg.isPending,
                            messageId = msg.id,
                            useTypewriter = msg.isPending,
                        )
                        AssistantReplyStatusRow(
                            isStreaming = msg.isPending,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        )
                    }
                } else {
                    ChatBubble(
                        text = msg.text,
                        isUser = msg.isUser,
                        isPending = msg.isPending,
                    )
                }
            }

            if ((isListening || isProcessing) && transcript.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                    ) {
                        ChatBubble(
                            text = transcript,
                            isUser = true,
                            isPending = true,
                        )
                        if (isProcessing) {
                            Row(
                                modifier =
                                    Modifier
                                        .padding(top = 6.dp)
                                        .fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
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
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(2f)
                    .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mic row first; pending images strip sits below the mic so it does not cover chat bubbles.
            // ImageAdd offset overlaps mic ring; VoiceMic on top so it receives touches near the + control.
            Box {
                ImageAddButton(
                    sheetOpen = attachmentSheetVisible,
                    onClick = { attachmentSheetVisible = !attachmentSheetVisible },
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = 84.dp + 4.dp,
                                y = 20.dp,
                            ),
                )
                VoiceMicButton(
                    isListening = isListening,
                    audioLevel = audioLevel,
                    hasPermission = permissionGranted,
                    onHoldStart = { speechViewModel.onHoldStart() },
                    onHoldEnd = { speechViewModel.onHoldEnd() },
                    onCancel = { speechViewModel.onHoldCancel() },
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .zIndex(1f),
                )
            }
            if (pendingImageUris.isNotEmpty()) {
                PendingImagesBar(
                    uris = pendingImageUris,
                    onRemove = { speechViewModel.removePendingImage(it) },
                    onAddMore = { attachmentSheetVisible = true },
                    onSend = { speechViewModel.sendPendingImages() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(top = 8.dp),
                )
            }
        }
    }

    if (attachmentSheetVisible) {
        AttachmentImageSheet(
            onDismiss = { attachmentSheetVisible = false },
            onAlbumClick = {
                pickImages.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            onCameraClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    launchCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onRecentImageClick = { uri ->
                speechViewModel.onImagesPicked(listOf(uri))
                attachmentSheetVisible = false
                focusManager.clearFocus()
            },
            canReadGallery = readImagesGranted,
        )
    }

    if (pendingConfirmation != null) {
        ConfirmActionsSheet(
            pending = pendingConfirmation!!,
            onDismiss = { speechViewModel.cancelPendingConfirmation() },
            onConfirm = { selected -> speechViewModel.confirmPendingActions(selected) },
            onCancel = { speechViewModel.cancelPendingConfirmation() },
        )
    }
}

private fun readImagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun ChatBubble(
    text: String,
    isUser: Boolean,
    isPending: Boolean,
    messageId: String? = null,
    useTypewriter: Boolean = false,
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
            when {
                !isUser && useTypewriter && messageId != null ->
                    TypewriterAssistantText(
                        messageId = messageId,
                        fullText = text,
                        color = textColor.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                else ->
                    Text(
                        text =
                            if (isPending && !useTypewriter) {
                                stringResource(R.string.chat_message_pending, text)
                            } else {
                                text
                            },
                        color = if (isPending) textColor.copy(alpha = 0.7f) else textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
            }
        }
    }
}
