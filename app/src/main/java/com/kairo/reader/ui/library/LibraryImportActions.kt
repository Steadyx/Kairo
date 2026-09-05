package com.kairo.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairo.reader.R

@Composable
internal fun ImportBookButton(
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.library_import_button),
            )
        }
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            stringResource(R.string.library_import_button),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ReadFromLinkButton(
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(
                Icons.Default.Link,
                contentDescription = stringResource(R.string.library_read_from_link_button),
            )
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            stringResource(R.string.library_read_from_link_button),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AddTextButton(
    onClick: () -> Unit,
    enabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(
                Icons.AutoMirrored.Filled.TextSnippet,
                contentDescription = stringResource(R.string.library_text_import_button),
            )
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TextSnippet,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp),
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            stringResource(R.string.library_text_import_button),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ImportSourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val containerColor =
        if (prominent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (prominent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 112.dp),
        shape = MaterialTheme.shapes.large,
        colors =
        CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                if (prominent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor =
                if (prominent) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(22.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ReadFromLinkDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_read_from_link_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.library_read_from_link_label)) },
                placeholder = {
                    Text(stringResource(R.string.library_read_from_link_placeholder))
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.library_read_from_link_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
