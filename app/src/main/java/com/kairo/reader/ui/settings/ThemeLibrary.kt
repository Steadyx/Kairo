@file:Suppress("LongMethod")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.MAX_SAVED_THEMES
import com.kairo.reader.core.model.MAX_THEME_NAME_LENGTH
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.ui.theme.materialColorScheme
import java.util.UUID

@Composable
internal fun ThemeLibrary(state: ThemeStudioState, validColor: Boolean, onSelect: () -> Unit) {
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val saved = state.draft.saved
    val room = saved.size < MAX_SAVED_THEMES
    Text(stringResource(R.string.theme_my_themes), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.theme_library_help), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = {
        naming = "save"
        targetId = null
        name = ""
    }, enabled = validColor && room && !state.locked) {
        Text(stringResource(R.string.theme_save_new))
    }
    if (!room) {
        Text(
            pluralStringResource(R.plurals.theme_library_full, MAX_SAVED_THEMES, MAX_SAVED_THEMES),
            style = MaterialTheme.typography.bodySmall
        )
    }
    val copySuffix = stringResource(R.string.theme_copy_suffix)
    saved.forEach { entry ->
        ThemeLibraryCard(entry, room, onLoad = {
            state.design(entry.design, resetEditor = true)
            onSelect()
        }, onAction = { action ->
            if (action == "delete") {
                state.change(state.draft.copy(saved = saved.filterNot { it.id == entry.id }))
            } else {
                targetId = entry.id
                naming = action
                name = if (action == "duplicate") "${entry.name} $copySuffix".take(MAX_THEME_NAME_LENGTH) else entry.name
            }
        })
    }
    if (naming != null) {
        val trimmed = name.trim()
        val duplicate = saved.any { it.name.equals(trimmed, ignoreCase = true) && (naming != "rename" || it.id != targetId) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(stringResource(if (naming == "rename") R.string.theme_rename else R.string.theme_save_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_THEME_NAME_LENGTH) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.theme_name)) },
                    isError = duplicate,
                    supportingText = { if (duplicate) Text(stringResource(R.string.theme_name_exists)) },
                )
            },
            confirmButton = {
                TextButton(enabled = trimmed.isNotEmpty() && !duplicate && !state.locked, onClick = {
                    val source = saved.find { it.id == targetId }
                    val next = if (naming == "rename") {
                        saved.map { if (it.id == targetId) it.copy(name = trimmed) else it }
                    } else {
                        saved + SavedTheme(UUID.randomUUID().toString(), trimmed, source?.design ?: state.draft.design)
                    }
                    state.change(state.draft.copy(saved = next))
                    naming = null
                }) { Text(stringResource(R.string.theme_done)) }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text(stringResource(R.string.theme_cancel)) } },
        )
    }
}

@Composable
private fun ThemeLibraryCard(entry: SavedTheme, canDuplicate: Boolean, onLoad: () -> Unit, onAction: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = remember(entry.design) { entry.design.theme.materialColorScheme(entry.design.custom) }
    Surface(onClick = onLoad, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(scheme.background, scheme.onBackground, scheme.primary).forEach {
                    Box(Modifier.size(20.dp).background(it, CircleShape))
                }
            }
            Text(entry.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall)
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.theme_manage_named, entry.name))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    listOf("rename" to R.string.theme_rename, "duplicate" to R.string.theme_duplicate, "delete" to R.string.theme_delete)
                        .forEach { (action, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                enabled = action != "duplicate" || canDuplicate,
                                onClick = {
                                    expanded = false
                                    onAction(action)
                                },
                            )
                        }
                }
            }
        }
    }
}
