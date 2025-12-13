package com.example.kairo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.UserPreferences

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onRsvpConfigChange: (RsvpConfig) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
    onRsvpFontSizeChange: (Float) -> Unit,
    onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
    onRsvpVerticalBiasChange: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Text("Words per minute: ${preferences.rsvpConfig.baseWpm}")
        Slider(
            value = preferences.rsvpConfig.baseWpm.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(baseWpm = it.toInt())) },
            valueRange = 150f..700f
        )

        Text("Words per frame: ${preferences.rsvpConfig.wordsPerFrame}")
        Slider(
            value = preferences.rsvpConfig.wordsPerFrame.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(wordsPerFrame = it.toInt().coerceIn(1, 3))) },
            valueRange = 1f..3f,
            steps = 1
        )

        Text("Reader font size: ${preferences.readerFontSizeSp.toInt()}sp")
        Slider(
            value = preferences.readerFontSizeSp,
            onValueChange = { onFontSizeChange(it) },
            valueRange = 14f..32f
        )

        // RSVP Font Settings Section
        Text("RSVP Settings", style = MaterialTheme.typography.titleMedium)

        Text("RSVP font size: ${preferences.rsvpFontSizeSp.toInt()}sp")
        Slider(
            value = preferences.rsvpFontSizeSp,
            onValueChange = { onRsvpFontSizeChange(it) },
            valueRange = 28f..64f
        )

        RsvpFontFamilySelector(
            selected = preferences.rsvpFontFamily,
            onFontFamilyChange = onRsvpFontFamilyChange
        )

        RsvpFontWeightSelector(
            selected = preferences.rsvpFontWeight,
            onFontWeightChange = onRsvpFontWeightChange
        )

        Text(
            "RSVP vertical position: ${(preferences.rsvpVerticalBias * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = preferences.rsvpVerticalBias,
            onValueChange = onRsvpVerticalBiasChange,
            valueRange = -0.6f..0.6f
        )

        ThemeSelector(
            selected = preferences.readerTheme,
            onThemeChange = onThemeChange
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Invert vertical swipe", style = MaterialTheme.typography.titleMedium)
                Text("Swipe up to move down, swipe down to move up.", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(
                checked = preferences.invertedScroll,
                onCheckedChange = onInvertedScrollChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset to defaults")
            }
            Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ThemeSelector(
    selected: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit
) {
    Column {
        Text("Reader theme", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderTheme.entries.forEach { theme ->
                OutlinedButton(
                    onClick = { onThemeChange(theme) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = theme.name.lowercase().replaceFirstChar { it.titlecase() },
                        color = if (theme == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun RsvpFontFamilySelector(
    selected: RsvpFontFamily,
    onFontFamilyChange: (RsvpFontFamily) -> Unit
) {
    Column {
        Text("RSVP font", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RsvpFontFamily.entries.forEach { family ->
                OutlinedButton(
                    onClick = { onFontFamilyChange(family) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = family.name.lowercase().replaceFirstChar { it.titlecase() },
                        color = if (family == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun RsvpFontWeightSelector(
    selected: RsvpFontWeight,
    onFontWeightChange: (RsvpFontWeight) -> Unit
) {
    Column {
        Text("RSVP font weight", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RsvpFontWeight.entries.forEach { weight ->
                OutlinedButton(
                    onClick = { onFontWeightChange(weight) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = weight.name.lowercase().replaceFirstChar { it.titlecase() },
                        color = if (weight == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
