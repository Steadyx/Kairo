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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHomeScreen(
    onOpenRsvp: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenFocus: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        SettingsNavRow(
            title = "RSVP settings",
            subtitle = "Speed, timing, readability shaping, RSVP typography",
            icon = Icons.Default.Settings,
            onClick = onOpenRsvp,
        )
        SettingsNavRow(
            title = "Reader settings",
            subtitle = "Font size, theme, scrolling",
            icon = Icons.Default.Settings,
            onClick = onOpenReader,
        )
        SettingsNavRow(
            title = "Focus settings",
            subtitle = "Minimal mode and Do Not Disturb options",
            icon = Icons.Default.Settings,
            onClick = onOpenFocus,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset to defaults")
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
