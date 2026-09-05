@file:Suppress("FunctionNaming", "LongParameterList")

package com.kairo.reader.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kairo.reader.R
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.UserPreferences

@Composable
fun ReaderSettingsScreen(
    preferences: UserPreferences,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.reader_settings_title),
        onBack = onBack,
    ) { modifier ->
        SearchableSettingsContent(page = SettingsSearchPage.READER, modifier = modifier) {
            ReaderSettingsContent(
                fontSizeSp = preferences.readerFontSizeSp,
                readerTheme = preferences.readerTheme,
                textBrightness = preferences.readerTextBrightness,
                invertedScroll = preferences.invertedScroll,
                onFontSizeChange = onFontSizeChange,
                onThemeChange = onThemeChange,
                onTextBrightnessChange = onTextBrightnessChange,
                onInvertedScrollChange = onInvertedScrollChange,
            )
        }
    }
}
