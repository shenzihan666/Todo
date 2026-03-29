package com.todolist.app.ui.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.todolist.app.R

private val thumbSize = 64.dp

@Composable
fun PendingImagesBar(
    uris: List<Uri>,
    onRemove: (Uri) -> Unit,
    onAddMore: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uris.isEmpty()) return

    val sendDesc = stringResource(R.string.content_desc_send_pending_images)
    val addMoreDesc = stringResource(R.string.content_desc_add_more_images)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        LazyRow(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(uris, key = { it.toString() }) { uri ->
                PendingThumb(
                    uri = uri,
                    onRemove = { onRemove(uri) },
                )
            }
            item {
                Box(
                    modifier =
                        Modifier
                            .size(thumbSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .clickable(onClick = onAddMore)
                            .semantics {
                                contentDescription = addMoreDesc
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            item {
                IconButton(
                    onClick = onSend,
                    modifier =
                        Modifier
                            .semantics {
                                contentDescription = sendDesc
                            },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingThumb(
    uri: Uri,
    onRemove: () -> Unit,
) {
    val removeDesc = stringResource(R.string.content_desc_remove_image)
    Box(modifier = Modifier.size(thumbSize)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
        )
        IconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .background(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                        shape = CircleShape,
                    )
                    .semantics {
                        contentDescription = removeDesc
                    },
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
