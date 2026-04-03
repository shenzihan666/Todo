package com.todolist.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.todolist.app.R
import kotlinx.coroutines.delay

private const val TypewriterDelayMs = 14L

/**
 * Reveals [fullText] one character at a time while the server buffer grows, so multi-token SSE
 * chunks still read as a typewriter.
 */
@Composable
fun TypewriterAssistantText(
    messageId: String,
    fullText: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var shown by remember(messageId) { mutableStateOf("") }
    LaunchedEffect(messageId) {
        snapshotFlow { fullText }.collect { target ->
            while (shown.length < target.length) {
                shown = target.take(shown.length + 1)
                delay(TypewriterDelayMs)
            }
        }
    }
    Text(text = shown, color = color, style = style, modifier = modifier)
}

@Composable
fun AssistantReplyStatusRow(
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    val doneTint = MaterialTheme.colorScheme.primary

    AnimatedContent(
        targetState = isStreaming,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(220))
            ) togetherWith fadeOut(animationSpec = tween(160))
        },
        label = "agent_reply_status",
        modifier = modifier.fillMaxWidth(),
    ) { streaming ->
        if (streaming) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = muted,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.agent_status_processing),
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = doneTint,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.agent_status_completed),
                    color = doneTint.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Shown under an assistant bubble when the user cancels the action sheet; persists in history. */
@Composable
fun AssistantReplyCancelledStatusRow(
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.agent_status_cancelled),
            color = tint.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Collapsible tool-call details under an assistant message. State is remembered per
 * [messageId] + index so it survives list updates when new messages arrive.
 */
@Composable
fun ToolInvocationSections(
    invocations: List<AgentToolInvocation>,
    messageId: String,
    modifier: Modifier = Modifier,
) {
    if (invocations.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        invocations.forEachIndexed { index, inv ->
            val stableKey = "$messageId-tool-$index-${inv.toolName}"
            ToolInvocationCollapseCard(
                invocation = inv,
                stableKey = stableKey,
            )
        }
    }
}

@Composable
private fun ToolInvocationCollapseCard(
    invocation: AgentToolInvocation,
    stableKey: String,
) {
    var expanded by remember(stableKey) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .rotate(if (expanded) 180f else 0f),
                    tint = muted.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.agent_tool_detail_title, invocation.toolName),
                    color = muted.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.agent_tool_args),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted.copy(alpha = 0.75f),
                    )
                    Text(
                        text = invocation.argsJson ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.agent_tool_result),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted.copy(alpha = 0.75f),
                    )
                    Text(
                        text = invocation.result ?: stringResource(R.string.agent_tool_calling),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
