package com.todolist.app.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val holdDesc = stringResource(R.string.voice_input_hold_desc)
    val cancelReleaseDesc = stringResource(R.string.voice_cancel_release)

    val density = LocalDensity.current
    val cancelThresholdPx = remember(density) { with(density) { 150.dp.toPx() } }

    /** True while finger is down — drives animation immediately (SpeechRecognizer lags). */
    var isHolding by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val active = isHolding || isListening

    val inCancelZone = isHolding && dragOffsetY <= -cancelThresholdPx

    val pulseColor by animateColorAsState(
        targetValue =
            if (inCancelZone) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        animationSpec = tween(220),
        label = "pulse_tint",
    )

    val micFillColor by animateColorAsState(
        targetValue =
            if (inCancelZone) {
                MaterialTheme.colorScheme.error
            } else if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        animationSpec = tween(220),
        label = "mic_fill",
    )

    val micIconTint by animateColorAsState(
        targetValue =
            when {
                inCancelZone -> MaterialTheme.colorScheme.onError
                active -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
        animationSpec = tween(220),
        label = "mic_icon_tint",
    )

    val redOverlayAlpha by animateFloatAsState(
        targetValue = if (inCancelZone) 0.42f else 0f,
        animationSpec = tween(200),
        label = "red_overlay",
    )

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

    var wasInCancelZone by remember { mutableStateOf(false) }
    LaunchedEffect(isHolding, inCancelZone) {
        if (isHolding && inCancelZone != wasInCancelZone) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            wasInCancelZone = inCancelZone
        }
        if (!isHolding) {
            wasInCancelZone = false
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

        val hintAlpha by animateFloatAsState(
            targetValue = if (isHolding && !inCancelZone) 0.55f else 0f,
            animationSpec = tween(150),
            label = "hint_alpha",
        )

        Text(
            text =
                if (inCancelZone) {
                    stringResource(R.string.voice_cancel_release)
                } else {
                    String.format(java.util.Locale.US, "%.2f s", recordingTime)
                },
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (inCancelZone) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier =
                Modifier
                    .padding(bottom = 8.dp)
                    .alpha(textAlpha),
        )

        Text(
            text = stringResource(R.string.voice_cancel_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .alpha(hintAlpha)
                    .padding(bottom = 8.dp),
        )

        Box(
            modifier =
                Modifier
                    .size(168.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription =
                            if (inCancelZone) {
                                cancelReleaseDesc
                            } else {
                                holdDesc
                            }
                    }
                    .pointerInput(hasPermission, cancelThresholdPx) {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!hasPermission) {
                                onRequestPermission()
                                return@awaitPointerEventScope
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            isHolding = true
                            dragOffsetY = 0f
                            onHoldStart()
                            var lastOffset = 0f
                            var cancelled = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: break
                                    lastOffset = change.position.y - down.position.y
                                    dragOffsetY = lastOffset
                                    if (!change.pressed) break
                                }
                                cancelled = lastOffset <= -cancelThresholdPx
                            } finally {
                                isHolding = false
                                dragOffsetY = 0f
                                if (cancelled) {
                                    onCancel()
                                } else {
                                    onHoldEnd()
                                }
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            // Radial red wash when sliding into cancel zone
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (redOverlayAlpha < 0.001f) return@drawBehind
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.maxDimension * 0.85f
                            drawCircle(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                Color(0xFFFF5252).copy(alpha = 0.55f),
                                                Color.Transparent,
                                            ),
                                        center = c,
                                        radius = r,
                                    ),
                                radius = r,
                                center = c,
                                alpha = redOverlayAlpha,
                            )
                        },
            )

            ListeningPulseRings(
                active = isHolding,
                ringPulse1 = ringPulse1,
                ringPulse2 = ringPulse2,
                ringBaseColor = pulseColor,
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
                            color = micFillColor,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = micIconTint,
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
    ringBaseColor: Color,
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
                            color = ringBaseColor.copy(alpha = 0.55f),
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
                            color = ringBaseColor.copy(alpha = 0.65f),
                            shape = CircleShape,
                        ),
            )
        }
    }
}
