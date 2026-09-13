package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.kairo.reader.R
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.ThemeDesign

@Composable
internal fun ThemeHistoryActions(state: ThemeStudioState, onRevert: () -> Unit) {
    val focus = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    Row {
        IconButton(onClick = {
            focus.clearFocus()
            state.undo()
        }, enabled = state.canUndo) {
            Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.theme_undo))
        }
        Box {
            IconButton(onClick = {
                focus.clearFocus()
                expanded = true
            }, enabled = !state.locked) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.theme_reset_options))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.theme_revert)) }, onClick = {
                    expanded = false
                    onRevert()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.theme_reset_colours)) }, onClick = {
                    expanded = false
                    state.reset(state.draft.copy(design = state.draft.design.copy(theme = ReaderTheme.CUSTOM, custom = CustomTheme())))
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.theme_reset_fonts)) }, onClick = {
                    expanded = false
                    val defaults = ThemeDesign()
                    state.reset(
                        state.draft.copy(
                            design = state.draft.design.copy(
                                readerFont = defaults.readerFont,
                                interfaceFont = defaults.interfaceFont,
                                timedFont = defaults.timedFont,
                            )
                        )
                    )
                })
            }
        }
    }
}
