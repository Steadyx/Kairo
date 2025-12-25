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
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
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
                        title = "Bookmarks",
                        subtitle = "Open saved bookmarks",
                        icon = Icons.Default.Bookmark,
                        onClick = onOpenBookmarks,
                    )
                    SettingsNavRow(
                        title = "Add bookmark",
                        subtitle = "Save this position",
                        icon = Icons.Default.Bookmark,
                        showChevron = false,
                        onClick = onAddBookmark,
                    )

                    SettingsNavRow(
                        title = "Reader settings",
                        subtitle = "Font size, theme, scrolling",
                        icon = Icons.Default.Settings,
                        onClick = { showReaderSettings = true },
                    )

                    SettingsSwitchRow(
                        title = "Focus mode",
                        subtitle = "Hide system chrome while reading.",
                        checked = focusModeEnabled,
                        onCheckedChange = onFocusModeEnabledChange,
                    )

                    SettingsNavRow(
                        title = "Table of contents",
                        subtitle = "Jump to a chapter",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        showChevron = false,
                        onClick = onShowToc,
                    )
                } else {
                    SettingsNavRow(
                        title = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        showChevron = false,
                        onClick = { showReaderSettings = false },
                    )
                    Text("Reader settings", style = MaterialTheme.typography.titleMedium)
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
