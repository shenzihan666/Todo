package com.todolist.app.ui.health

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.todolist.app.R

@Composable
fun HealthRoute(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HealthScreen(
        modifier = modifier,
        onNavigateToSettings = onNavigateToSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeTitle = stringResource(R.string.home_title)
    val settingsDesc = stringResource(R.string.content_desc_open_settings)
    var gearRotationTarget by remember { mutableFloatStateOf(0f) }
    val gearRotation by animateFloatAsState(
        targetValue = gearRotationTarget,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "settings_gear_rotation",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(homeTitle) },
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
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
