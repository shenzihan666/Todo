package com.todolist.app.ui.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.todolist.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentImageSheet(
    onDismiss: () -> Unit,
    onAlbumClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRecentImageClick: (Uri) -> Unit,
    canReadGallery: Boolean,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var recentUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(canReadGallery) {
        recentUris =
            if (canReadGallery) {
                RecentImages.loadRecentUris(context)
            } else {
                emptyList()
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AttachmentTile(
                    label = stringResource(R.string.attachment_camera),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f),
                )
                AttachmentTile(
                    label = stringResource(R.string.attachment_album),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = onAlbumClick,
                    modifier = Modifier.weight(1f),
                )
            }

            if (canReadGallery && recentUris.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.attachment_recent_photos),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(recentUris, key = { it.toString() }) { uri ->
                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onRecentImageClick(uri) },
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .clickable(onClick = onClick)
                .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
