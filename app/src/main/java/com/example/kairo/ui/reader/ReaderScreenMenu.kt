package com.example.kairo.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.R
import com.example.kairo.ui.settings.ReaderSettingsContent
import com.example.kairo.ui.settings.SettingsNavRow
import com.example.kairo.ui.settings.SettingsSwitchRow

@Composable
internal fun ReaderMenuOverlay(
    fontSizeSp: Float,
    readerTheme: ReaderTheme,
    textBrightness: Float,
    invertedScroll: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
    focusModeEnabled: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onAddBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onShowToc: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showReaderSettings by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        )

        Surface(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                )

                if (!showReaderSettings) {
                    SettingsNavRow(
                        title = stringResource(R.string.library_tab_bookmarks),
                        subtitle = stringResource(R.string.library_bookmarks_subtitle),
                        icon = Icons.Default.Bookmark,
                        onClick = onOpenBookmarks,
                    )
                    SettingsNavRow(
                        title = stringResource(R.string.reader_add_bookmark),
                        subtitle = stringResource(R.string.reader_add_bookmark_subtitle),
                        icon = Icons.Default.Bookmark,
                        showChevron = false,
                        onClick = onAddBookmark,
                    )

                    SettingsNavRow(
                        title = stringResource(R.string.reader_settings_title),
                        subtitle = stringResource(R.string.reader_settings_subtitle),
                        icon = Icons.Default.Settings,
                        onClick = { showReaderSettings = true },
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.focus_mode_title),
                        subtitle = stringResource(R.string.focus_mode_subtitle),
                        checked = focusModeEnabled,
                        onCheckedChange = onFocusModeEnabledChange,
                    )

                    SettingsNavRow(
                        title = stringResource(R.string.reader_toc_title),
                        subtitle = stringResource(R.string.reader_toc_subtitle),
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        showChevron = false,
                        onClick = onShowToc,
                    )
                } else {
                    SettingsNavRow(
                        title = stringResource(R.string.action_back),
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        showChevron = false,
                        onClick = { showReaderSettings = false },
                    )
                    Text(
                        stringResource(R.string.reader_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ReaderSettingsContent(
                        fontSizeSp = fontSizeSp,
                        readerTheme = readerTheme,
                        textBrightness = textBrightness,
                        invertedScroll = invertedScroll,
                        onFontSizeChange = onFontSizeChange,
                        onThemeChange = onThemeChange,
                        onTextBrightnessChange = onTextBrightnessChange,
                        onInvertedScrollChange = onInvertedScrollChange,
                    )
                }
            }
        }
    }
}
