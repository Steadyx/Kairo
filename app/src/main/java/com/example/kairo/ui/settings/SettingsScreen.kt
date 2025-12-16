package com.example.kairo.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.UserPreferences
import com.example.kairo.core.rsvp.RsvpPaceEstimator

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
    onRsvpHorizontalBiasChange: (Float) -> Unit,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onFocusHideStatusBarChange: (Boolean) -> Unit,
    onFocusPauseNotificationsChange: (Boolean) -> Unit,
    onFocusApplyInReaderChange: (Boolean) -> Unit,
    onFocusApplyInRsvpChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val hasDndAccess = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .isNotificationPolicyAccessGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Text("RSVP Timing (Comprehension)", style = MaterialTheme.typography.titleMedium)

        val estimatedWpm = remember(preferences.rsvpConfig) {
            RsvpPaceEstimator.estimateWpm(preferences.rsvpConfig)
        }
        Text("Estimated pace: $estimatedWpm WPM", style = MaterialTheme.typography.bodyMedium)

        Text("Tempo: ${preferences.rsvpConfig.tempoMsPerWord}ms per baseline word")
        Text(
            "Lower tempo = faster. This is the overall speed dial; everything else shapes readability.",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = preferences.rsvpConfig.tempoMsPerWord.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(tempoMsPerWord = it.toLong().coerceIn(60L, 240L))) },
            valueRange = 60f..240f
        )

        Text("Minimum word time: ${preferences.rsvpConfig.minWordMs}ms")
        Text(
            "Hard floor for any displayed word; prevents flashing at high speeds.",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = preferences.rsvpConfig.minWordMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(minWordMs = it.toLong().coerceIn(30L, 140L))) },
            valueRange = 30f..140f
        )

        Text("Long-word minimum: ${preferences.rsvpConfig.longWordMinMs}ms (≥ ${preferences.rsvpConfig.longWordChars} chars)")
        Text(
            "Ensures long/complex words feel readable even at very high speed.",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = preferences.rsvpConfig.longWordMinMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(longWordMinMs = it.toLong().coerceIn(80L, 300L))) },
            valueRange = 80f..300f
        )
        Slider(
            value = preferences.rsvpConfig.longWordChars.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(longWordChars = it.toInt().coerceIn(8, 14))) },
            valueRange = 8f..14f,
            steps = 5
        )

        Text("Syllable boost: +${preferences.rsvpConfig.syllableExtraMs}ms per extra syllable")
        Slider(
            value = preferences.rsvpConfig.syllableExtraMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(syllableExtraMs = it.toLong().coerceIn(0L, 45L))) },
            valueRange = 0f..45f
        )

        Text("Rarity boost: up to +${preferences.rsvpConfig.rarityExtraMaxMs}ms (rare words)")
        Slider(
            value = preferences.rsvpConfig.rarityExtraMaxMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(rarityExtraMaxMs = it.toLong().coerceIn(0L, 200L))) },
            valueRange = 0f..200f
        )

        Text("Complexity strength: ${(preferences.rsvpConfig.complexityStrength * 100).toInt()}%")
        Slider(
            value = (preferences.rsvpConfig.complexityStrength * 100).toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(complexityStrength = (it / 100.0).coerceIn(0.0, 1.0))) },
            valueRange = 0f..100f
        )

        Text("Punctuation Pauses", style = MaterialTheme.typography.titleSmall)
        Text("Comma: ${preferences.rsvpConfig.commaPauseMs}ms")
        Slider(
            value = preferences.rsvpConfig.commaPauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(commaPauseMs = it.toLong().coerceIn(0L, 260L))) },
            valueRange = 0f..260f
        )
        Text("Dash/semicolon/colon: ${preferences.rsvpConfig.dashPauseMs} / ${preferences.rsvpConfig.semicolonPauseMs} / ${preferences.rsvpConfig.colonPauseMs} ms")
        Slider(
            value = preferences.rsvpConfig.dashPauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(dashPauseMs = it.toLong().coerceIn(0L, 320L))) },
            valueRange = 0f..320f
        )
        Slider(
            value = preferences.rsvpConfig.semicolonPauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(semicolonPauseMs = it.toLong().coerceIn(0L, 360L))) },
            valueRange = 0f..360f
        )
        Slider(
            value = preferences.rsvpConfig.colonPauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(colonPauseMs = it.toLong().coerceIn(0L, 360L))) },
            valueRange = 0f..360f
        )
        Text("Sentence end: ${preferences.rsvpConfig.sentenceEndPauseMs}ms")
        Slider(
            value = preferences.rsvpConfig.sentenceEndPauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(sentenceEndPauseMs = it.toLong().coerceIn(0L, 500L))) },
            valueRange = 0f..500f
        )
        Text("Parentheses: ${preferences.rsvpConfig.parenthesesPauseMs}ms, Quotes: ${preferences.rsvpConfig.quotePauseMs}ms")
        Slider(
            value = preferences.rsvpConfig.parenthesesPauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(parenthesesPauseMs = it.toLong().coerceIn(0L, 320L))) },
            valueRange = 0f..320f
        )
        Slider(
            value = preferences.rsvpConfig.quotePauseMs.toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(quotePauseMs = it.toLong().coerceIn(0L, 200L))) },
            valueRange = 0f..200f
        )

        Text("Pause scaling: ${(preferences.rsvpConfig.pauseScaleExponent * 100).toInt()}% (high-speed compression)")
        Slider(
            value = (preferences.rsvpConfig.pauseScaleExponent * 100).toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(pauseScaleExponent = (it / 100.0).coerceIn(0.2, 0.9))) },
            valueRange = 20f..90f
        )

        Text("Context shaping", style = MaterialTheme.typography.titleSmall)
        Text("Parentheticals: +${((preferences.rsvpConfig.parentheticalMultiplier - 1.0) * 100).toInt()}%")
        Slider(
            value = ((preferences.rsvpConfig.parentheticalMultiplier - 1.0) * 100).toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(parentheticalMultiplier = (1.0 + it / 100.0).coerceIn(1.0, 1.35))) },
            valueRange = 0f..35f
        )
        Text("Dialogue pace: ${(preferences.rsvpConfig.dialogueMultiplier * 100).toInt()}%")
        Slider(
            value = (preferences.rsvpConfig.dialogueMultiplier * 100).toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(dialogueMultiplier = (it / 100.0).coerceIn(0.85, 1.05))) },
            valueRange = 85f..105f
        )

        Text("Rhythm stability: ${(preferences.rsvpConfig.smoothingAlpha * 100).toInt()}%")
        Slider(
            value = (preferences.rsvpConfig.smoothingAlpha * 100).toFloat(),
            onValueChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(smoothingAlpha = (it / 100.0).coerceIn(0.0, 1.0))) },
            valueRange = 0f..100f
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Phrase chunking")
                Text("Shows short 2-word units to reduce flicker.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = preferences.rsvpConfig.enablePhraseChunking,
                onCheckedChange = { onRsvpConfigChange(preferences.rsvpConfig.copy(enablePhraseChunking = it)) }
            )
        }

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

        Text(
            "RSVP left bias: ${(preferences.rsvpHorizontalBias * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = preferences.rsvpHorizontalBias,
            onValueChange = onRsvpHorizontalBiasChange,
            valueRange = -0.6f..0.6f
        )

        ThemeSelector(
            selected = preferences.readerTheme,
            onThemeChange = onThemeChange
        )

        // Focus Mode
        Text("Focus Mode", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Enable focus mode", style = MaterialTheme.typography.titleMedium)
                Text("Hide system chrome while reading.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = preferences.focusModeEnabled,
                onCheckedChange = onFocusModeEnabledChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Hide status bar", style = MaterialTheme.typography.titleMedium)
                Text("Hides the top bar (time, notifications).", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = preferences.focusHideStatusBar,
                onCheckedChange = onFocusHideStatusBarChange,
                enabled = preferences.focusModeEnabled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Pause notifications", style = MaterialTheme.typography.titleMedium)
                Text("Uses Do Not Disturb while focus mode is active.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = preferences.focusPauseNotifications,
                onCheckedChange = onFocusPauseNotificationsChange,
                enabled = preferences.focusModeEnabled
            )
        }

        if (preferences.focusModeEnabled && preferences.focusPauseNotifications && !hasDndAccess) {
            Text(
                "Grant Do Not Disturb access to pause notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ) {
                Text("Open DND access settings")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Apply in Reader", style = MaterialTheme.typography.titleMedium)
                Text("Use focus mode in the scroll reader.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = preferences.focusApplyInReader,
                onCheckedChange = onFocusApplyInReaderChange,
                enabled = preferences.focusModeEnabled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Apply in RSVP", style = MaterialTheme.typography.titleMedium)
                Text("Use focus mode in RSVP playback.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = preferences.focusApplyInRsvp,
                onCheckedChange = onFocusApplyInRsvpChange,
                enabled = preferences.focusModeEnabled
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Invert vertical swipe", style = MaterialTheme.typography.titleMedium)
                Text("Swipe up to move down, swipe down to move up.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
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
