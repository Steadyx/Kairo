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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.decodeCustomTheme
import com.kairo.reader.core.model.encode
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.theme.materialColorScheme

@Composable
fun ThemeSettingsScreen(
    preferences: UserPreferences,
    onApply: (ReaderTheme, CustomTheme, RsvpFontFamily, RsvpFontFamily, RsvpFontFamily) -> Unit,
    onBack: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(preferences.customTheme.encode()) }
    var selected by rememberSaveable { mutableStateOf(preferences.readerTheme) }
    var readerFont by rememberSaveable { mutableStateOf(preferences.readerFontFamily) }
    var interfaceFont by rememberSaveable { mutableStateOf(preferences.interfaceFontFamily) }
    var timedFont by rememberSaveable { mutableStateOf(preferences.rsvpFontFamily) }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    var validColor by rememberSaveable { mutableStateOf(true) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val custom = decodeCustomTheme(draft)
    val dirty = draft != preferences.customTheme.encode() ||
        selected != preferences.readerTheme ||
        readerFont != preferences.readerFontFamily ||
        interfaceFont != preferences.interfaceFontFamily ||
        timedFont != preferences.rsvpFontFamily
    val back = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = back)
    val searchTarget = LocalSettingsSearchTarget.current
    var section by rememberSaveable {
        mutableStateOf(
            when {
                searchTarget?.id == "theme.fonts" -> ThemeStudioSection.FONTS
                searchTarget?.id == "theme.custom" || selected == ReaderTheme.CUSTOM -> ThemeStudioSection.COLOURS
                else -> ThemeStudioSection.PALETTES
            }
        )
    }
    val workingCustom = if (selected == ReaderTheme.CUSTOM) {
        custom
    } else {
        CustomTheme(background = selected.materialColorScheme().background.toArgb(), harmony = custom.harmony)
    }
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
                revision = revision,
                onSectionChange = { section = it },
                preview = {
                    KairoTheme(selected, custom, readerFont, interfaceFont) {
                        ThemeLivePreview(readerFont, timedFont, section, selected == ReaderTheme.CUSTOM)
                    }
                },
            ) {
                when (section) {
                    ThemeStudioSection.PALETTES -> ThemePresetSelector(selected, custom) {
                        selected = it
                        revision++
                    }
                    ThemeStudioSection.COLOURS -> {
                        HarmonySelector(workingCustom.harmony) {
                            draft = workingCustom.copy(harmony = it).encode()
                            selected = ReaderTheme.CUSTOM
                        }
                        ThemePaletteEditor(workingCustom, onValidityChange = { validColor = it }) {
                            draft = it.encode()
                            selected = ReaderTheme.CUSTOM
                        }
                    }
                    ThemeStudioSection.FONTS -> {
                        ThemeFontSelector(stringResource(R.string.theme_font_reader), readerFont) { readerFont = it }
                        ThemeFontSelector(stringResource(R.string.theme_font_interface), interfaceFont) { interfaceFont = it }
                        ThemeFontSelector(stringResource(R.string.theme_font_timed), timedFont) { timedFont = it }
                    }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        revision++
                        draft = CustomTheme().encode()
                        selected = ReaderTheme.CUSTOM
                        readerFont = RsvpFontFamily.MERRIWEATHER
                        interfaceFont = RsvpFontFamily.SYSTEM_SANS
                        timedFont = RsvpFontFamily.INTER
                    }) { Text(stringResource(R.string.theme_start_again)) }
                    Button(onClick = { onApply(selected, custom, readerFont, interfaceFont, timedFont) }, enabled = validColor) {
                        Text(stringResource(R.string.theme_apply))
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
