@file:Suppress("LongMethod")

package com.kairo.reader.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.core.model.ThemeDesign
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.themeDesign
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.theme.copyAsCustom
import java.io.IOException
import kotlinx.coroutines.launch

@Composable
fun ThemeSettingsScreen(
    preferences: UserPreferences,
    onApply: suspend (ThemeDesign, List<SavedTheme>) -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val applied = ThemeStudioDraft(preferences.themeDesign(), preferences.savedThemes)
    val state = rememberSaveable(saver = ThemeStudioState.Saver) { ThemeStudioState(applied) }
    val design = state.draft.design
    var validColor by rememberSaveable { mutableStateOf(true) }
    var validityRevision by remember { mutableIntStateOf(state.revision) }
    LaunchedEffect(state.revision) {
        if (validityRevision != state.revision) {
            validColor = true
            validityRevision = state.revision
        }
    }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val dirty = state.draft != applied
    val back = {
        if (!state.locked) {
            if (dirty || !validColor) confirmDiscard = true else onBack()
        }
    }
    BackHandler(onBack = back)
    val scope = rememberCoroutineScope()
    val searchTarget = LocalSettingsSearchTarget.current
    var section by rememberSaveable {
        mutableStateOf(
            when {
                searchTarget?.id == "theme.fonts" -> ThemeStudioSection.FONTS
                searchTarget?.id == "theme.custom" || design.theme == ReaderTheme.CUSTOM -> ThemeStudioSection.COLOURS
                else -> ThemeStudioSection.PALETTES
            }
        )
    }
    val workingCustom = design.theme.copyAsCustom(design.custom)
    SettingsScaffold(
        title = stringResource(R.string.theme_settings_title),
        onBack = back,
        compactHeader = true,
        maxContentWidth = 1040.dp
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            ThemeStudioWorkspace(
                modifier = Modifier.weight(1f),
                section = section,
                revision = state.revision,
                onSectionChange = {
                    focus.clearFocus()
                    state.endEdit()
                    section = it
                },
                preview = {
                    KairoTheme(design.theme, design.custom, design.readerFont, design.interfaceFont) {
                        ThemeLivePreview(preferences, design)
                    }
                },
            ) {
                when (section) {
                    ThemeStudioSection.PALETTES -> {
                        ThemePresetSelector(design.theme, design.custom) {
                            state.design(design.copy(theme = it), resetEditor = true)
                        }
                        ThemeLibrary(state, validColor, onSelect = { section = ThemeStudioSection.COLOURS })
                    }
                    ThemeStudioSection.COLOURS -> {
                        if (design.theme != ReaderTheme.CUSTOM) {
                            Text(
                                stringResource(R.string.theme_copy_help),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = {
                                state.design(design.copy(theme = ReaderTheme.CUSTOM, custom = workingCustom), resetEditor = true)
                            }) { Text(stringResource(R.string.theme_edit_copy)) }
                        }
                        HarmonySelector(workingCustom.harmony) {
                            state.design(design.copy(theme = ReaderTheme.CUSTOM, custom = workingCustom.copy(harmony = it)))
                        }
                        ThemePaletteEditor(
                            theme = workingCustom,
                            onValidityChange = { validColor = it },
                            onEditStart = state::beginEdit,
                            onEditFinished = state::endEdit,
                            onChange = { state.design(design.copy(custom = it, theme = ReaderTheme.CUSTOM)) },
                        )
                    }
                    ThemeStudioSection.FONTS -> {
                        ThemeFontSelector(stringResource(R.string.theme_font_reader), design.readerFont) {
                            state.design(design.copy(readerFont = it))
                        }
                        ThemeFontSelector(stringResource(R.string.theme_font_interface), design.interfaceFont) {
                            state.design(design.copy(interfaceFont = it))
                        }
                        ThemeFontSelector(stringResource(R.string.theme_font_timed), design.timedFont) {
                            state.design(design.copy(timedFont = it))
                        }
                    }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (saveFailed) Text(stringResource(R.string.theme_save_error))
                    if (!validColor && section != ThemeStudioSection.COLOURS) {
                        TextButton(onClick = { section = ThemeStudioSection.COLOURS }) { Text(stringResource(R.string.theme_hex_error)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ThemeHistoryActions(state, onRevert = { state.reset(applied) })
                        Button(
                            onClick = {
                                state.endEdit()
                                state.locked = true
                                saveFailed = false
                                val snapshot = state.draft
                                scope.launch {
                                    try {
                                        onApply(snapshot.design, snapshot.saved)
                                        onBack()
                                    } catch (_: IOException) {
                                        saveFailed = true
                                    } finally {
                                        state.locked = false
                                    }
                                }
                            },
                            enabled = validColor && !state.locked,
                        ) { Text(stringResource(if (state.locked) R.string.theme_saving else R.string.theme_apply)) }
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.theme_discard_title)) },
            text = { Text(stringResource(R.string.theme_discard_body)) },
            confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.theme_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.theme_keep_editing)) } },
        )
    }
}
