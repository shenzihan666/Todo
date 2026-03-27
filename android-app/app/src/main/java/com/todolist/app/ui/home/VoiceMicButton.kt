package com.todolist.app.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.todolist.app.R
import kotlinx.coroutines.delay

@Composable
fun VoiceMicButton(
    isListening: Boolean,
    audioLevel: Float,
    hasPermission: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val holdDesc = stringResource(R.string.voice_input_hold_desc)

    /** True while finger is down — drives animation immediately (SpeechRecognizer lags). */
    var isHolding by remember { mutableStateOf(false) }
    val active = isHolding || isListening

    var recordingTime by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            recordingTime = 0f
            val startTime = System.currentTimeMillis()
            while (true) {
                delay(10)
                recordingTime = (System.currentTimeMillis() - startTime) / 1000f
            }
        } else {
            recordingTime = 0f
        }
    }

    val pulseFast = rememberInfiniteTransition(label = "mic_ring_fast")
    val ringPulse1 by pulseFast.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "r1",
    )
    val pulseSlow = rememberInfiniteTransition(label = "mic_ring_slow")
    val ringPulse2 by pulseSlow.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(720, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "r2",
    )

    val micScale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "mic_press",
    )
    val levelBoost = 1f + audioLevel * 0.18f
    val micCombinedScale = micScale * if (active) levelBoost else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        val textAlpha by animateFloatAsState(
            targetValue = if (isHolding) 1f else 0f,
            animationSpec = tween(150),
            label = "text_alpha",
        )

        Text(
            text = String.format(java.util.Locale.US, "%.2f s", recordingTime),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .alpha(textAlpha),
        )

        Box(
            modifier =
                Modifier
                    .size(168.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = holdDesc
                    }
                    .pointerInput(hasPermission) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (!hasPermission) {
                                onRequestPermission()
                                return@awaitEachGesture
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            isHolding = true
                            onHoldStart()
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                isHolding = false
                                onHoldEnd()
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            ListeningPulseRings(
                active = active,
                ringPulse1 = ringPulse1,
                ringPulse2 = ringPulse2,
            )

            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = micCombinedScale
                            scaleY = micCombinedScale
                        }
                        .background(
                            color =
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint =
                        if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun ListeningPulseRings(
    active: Boolean,
    ringPulse1: Float,
    ringPulse2: Float,
) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.9f, animationSpec = tween(160)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(148.dp)
                        .scale(ringPulse2)
                        .alpha(0.28f)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            shape = CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .scale(ringPulse1)
                        .alpha(0.42f)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            shape = CircleShape,
                        ),
            )
        }
    }
}
