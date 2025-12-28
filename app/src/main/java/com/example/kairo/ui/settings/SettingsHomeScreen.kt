@file:Suppress("FunctionNaming")

package com.example.kairo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kairo.R

@Composable
fun SettingsHomeScreen(
    onOpenRsvp: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenLanguage: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val languageLabel = resolveLanguageLabel(context, getAppLanguageTag())

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        SettingsNavRow(
            title = stringResource(R.string.settings_language_title),
            subtitle = languageLabel,
            icon = Icons.Default.Language,
            onClick = onOpenLanguage,
        )

        SettingsNavRow(
            title = stringResource(R.string.rsvp_settings_title),
            subtitle = stringResource(R.string.settings_rsvp_subtitle),
            icon = Icons.Default.Settings,
            onClick = onOpenRsvp,
        )
        SettingsNavRow(
            title = stringResource(R.string.reader_settings_title),
            subtitle = stringResource(R.string.reader_settings_subtitle),
            icon = Icons.Default.Settings,
            onClick = onOpenReader,
        )
        SettingsNavRow(
            title = stringResource(R.string.focus_settings_title),
            subtitle = stringResource(R.string.focus_settings_subtitle),
            icon = Icons.Default.Settings,
            onClick = onOpenFocus,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reset_defaults))
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_done))
        }
    }
}
