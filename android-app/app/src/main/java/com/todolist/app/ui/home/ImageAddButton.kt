package com.todolist.app.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.todolist.app.R

@Composable
fun ImageAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.content_desc_add_image)
    IconButton(
        onClick = onClick,
        modifier =
            modifier.semantics {
                contentDescription = desc
            },
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
        )
    }
}
