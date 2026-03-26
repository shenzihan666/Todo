package com.todolist.app.ui.health

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun HealthRoute(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as TodoListApplication
    val speechViewModel: SpeechViewModel = viewModel(factory = app.speechViewModelFactory())
    HealthScreen(
        modifier = modifier,
        onNavigateToSettings = onNavigateToSettings,
        speechViewModel = speechViewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
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
    val isListening by speechViewModel.isListening.collectAsStateWithLifecycle()
    val audioLevel by speechViewModel.audioLevel.collectAsStateWithLifecycle()
    val errorMessage by speechViewModel.errorMessage.collectAsStateWithLifecycle()

    val displayText = errorMessage ?: transcript

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
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (displayText.isNotEmpty()) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
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
