package com.example.kairo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.UserPreferences

@Composable
fun ReaderSettingsScreen(
    preferences: UserPreferences,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    SettingsScaffold(title = "Reader settings", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReaderSettingsContent(
                fontSizeSp = preferences.readerFontSizeSp,
                readerTheme = preferences.readerTheme,
                invertedScroll = preferences.invertedScroll,
                onFontSizeChange = onFontSizeChange,
                onThemeChange = onThemeChange,
                onInvertedScrollChange = onInvertedScrollChange
            )
        }
    }
}
