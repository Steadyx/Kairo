@file:Suppress("MagicNumber")

package com.kairo.reader.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.ui.theme.composeFontFamily

@Composable
internal fun ThemeLivePreview(reader: RsvpFontFamily, timed: RsvpFontFamily, section: ThemeStudioSection, custom: Boolean) {
    var showContrast by rememberSaveable { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.testTag("theme-live-preview"),
        color = scheme.background,
        contentColor = scheme.onBackground,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(if (largeText) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!largeText) {
                Text(
                    stringResource(R.string.theme_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary
                )
            }
            Text(stringResource(R.string.theme_live_sample), fontFamily = reader.composeFontFamily(), fontSize = 26.sp, lineHeight = 32.sp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.theme_preview_word),
                    fontFamily = timed.composeFontFamily(),
                    color = scheme.tertiary,
                    fontSize = 20.sp
                )
                if (custom && section != ThemeStudioSection.FONTS) {
                    TextButton(onClick = { showContrast = true }) {
                        Text(
                            stringResource(R.string.theme_contrast_aa),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onBackground
                        )
                    }
                }
            }
        }
    }
    if (showContrast) {
        AlertDialog(
            onDismissRequest = { showContrast = false },
            title = { Text(stringResource(R.string.theme_contrast_aa)) },
            text = { Text(stringResource(R.string.theme_reading_contrast_help)) },
            confirmButton = { TextButton(onClick = { showContrast = false }) { Text(stringResource(R.string.theme_done)) } },
        )
    }
}
