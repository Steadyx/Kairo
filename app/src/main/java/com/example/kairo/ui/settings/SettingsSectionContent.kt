package com.example.kairo.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.rsvp.RsvpPaceEstimator

@Composable
fun ReaderSettingsContent(
    fontSizeSp: Float,
    readerTheme: ReaderTheme,
    textBrightness: Float,
    invertedScroll: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit
) {
    SettingsSliderRow(
        title = "Font size",
        valueLabel = "${fontSizeSp.toInt()}sp",
        value = fontSizeSp,
        onValueChange = { onFontSizeChange(it.coerceIn(14f, 32f)) },
        valueRange = 14f..32f
    )

    ThemeSelector(selected = readerTheme, onThemeChange = onThemeChange)

    SettingsSliderRow(
        title = "Text brightness",
        subtitle = "Dims the reader text without changing the theme.",
        valueLabel = "${(textBrightness.coerceIn(0.55f, 1.0f) * 100).toInt()}%",
        value = textBrightness.coerceIn(0.55f, 1.0f),
        onValueChange = { onTextBrightnessChange(it.coerceIn(0.55f, 1.0f)) },
        valueRange = 0.55f..1.0f
    )

    Text("Scrolling", style = MaterialTheme.typography.titleMedium)
    SettingsSwitchRow(
        title = "Invert vertical swipe",
        subtitle = "Swipe up to move down, swipe down to move up.",
        checked = invertedScroll,
        onCheckedChange = onInvertedScrollChange
    )
}

@Composable
fun FocusSettingsContent(
    focusModeEnabled: Boolean,
    focusHideStatusBar: Boolean,
    focusPauseNotifications: Boolean,
    focusApplyInReader: Boolean,
    focusApplyInRsvp: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onFocusHideStatusBarChange: (Boolean) -> Unit,
    onFocusPauseNotificationsChange: (Boolean) -> Unit,
    onFocusApplyInReaderChange: (Boolean) -> Unit,
    onFocusApplyInRsvpChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val hasDndAccess = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .isNotificationPolicyAccessGranted

    SettingsSwitchRow(
        title = "Enable focus mode",
        subtitle = "Hide system chrome while reading.",
        checked = focusModeEnabled,
        onCheckedChange = onFocusModeEnabledChange
    )

    SettingsSwitchRow(
        title = "Hide status bar",
        subtitle = "Hides the top bar (time, notifications).",
        checked = focusHideStatusBar,
        onCheckedChange = onFocusHideStatusBarChange,
        enabled = focusModeEnabled
    )

    SettingsSwitchRow(
        title = "Pause notifications",
        subtitle = "Uses Do Not Disturb while focus mode is active.",
        checked = focusPauseNotifications,
        onCheckedChange = onFocusPauseNotificationsChange,
        enabled = focusModeEnabled
    )

    if (focusModeEnabled && focusPauseNotifications && !hasDndAccess) {
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

    Spacer(modifier = Modifier.height(8.dp))
    Text("Apply focus mode", style = MaterialTheme.typography.titleMedium)

    SettingsSwitchRow(
        title = "Apply in Reader",
        subtitle = "Use focus mode in the scroll reader.",
        checked = focusApplyInReader,
        onCheckedChange = onFocusApplyInReaderChange,
        enabled = focusModeEnabled
    )
    SettingsSwitchRow(
        title = "Apply in RSVP",
        subtitle = "Use focus mode in RSVP playback.",
        checked = focusApplyInRsvp,
        onCheckedChange = onFocusApplyInRsvpChange,
        enabled = focusModeEnabled
    )
}

@Composable
fun RsvpSettingsContent(
    config: RsvpConfig,
    unlockExtremeSpeed: Boolean,
    rsvpFontSizeSp: Float,
    rsvpTextBrightness: Float,
    rsvpFontFamily: RsvpFontFamily,
    rsvpFontWeight: RsvpFontWeight,
    rsvpVerticalBias: Float,
    rsvpHorizontalBias: Float,
    onConfigChange: (RsvpConfig) -> Unit,
    onUnlockExtremeSpeedChange: (Boolean) -> Unit,
    onRsvpFontSizeChange: (Float) -> Unit,
    onRsvpTextBrightnessChange: (Float) -> Unit,
    onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
    onRsvpVerticalBiasChange: (Float) -> Unit,
    onRsvpHorizontalBiasChange: (Float) -> Unit
) {
    fun updateConfig(updater: (RsvpConfig) -> RsvpConfig) {
        onConfigChange(updater(config))
    }

    val estimatedWpm = remember(config) { RsvpPaceEstimator.estimateWpm(config) }
    Text("Estimated pace: $estimatedWpm WPM", style = MaterialTheme.typography.bodyMedium)

    SettingsSwitchRow(
        title = "Unlock extreme speeds",
        subtitle = "Allows very high speeds (can quickly become unreadable).",
        checked = unlockExtremeSpeed,
        onCheckedChange = { enabled ->
            onUnlockExtremeSpeedChange(enabled)
            if (!enabled && config.tempoMsPerWord < 30L) {
                updateConfig { it.copy(tempoMsPerWord = 30L) }
            }
        }
    )

    val minTempoMs = if (unlockExtremeSpeed) 10L else 30L
    SettingsSliderRow(
        title = "Tempo",
        subtitle = "Lower tempo = faster. This is the overall speed dial; everything else shapes readability.",
        valueLabel = "${config.tempoMsPerWord}ms",
        value = config.tempoMsPerWord.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(tempoMsPerWord = newValue.toLong().coerceIn(minTempoMs, 240L)) }
        },
        valueRange = minTempoMs.toFloat()..240f
    )

    Text("Readability floors", style = MaterialTheme.typography.titleSmall)
    SettingsSliderRow(
        title = "Minimum word time",
        subtitle = "Hard floor for any displayed word; prevents flashing at high speeds.",
        valueLabel = "${config.minWordMs}ms",
        value = config.minWordMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(minWordMs = newValue.toLong().coerceIn(30L, 140L)) }
        },
        valueRange = 30f..140f
    )
    SettingsSliderRow(
        title = "Long-word minimum",
        subtitle = "Ensures long/complex words feel readable even at very high speed.",
        valueLabel = "${config.longWordMinMs}ms",
        value = config.longWordMinMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(longWordMinMs = newValue.toLong().coerceIn(80L, 300L)) }
        },
        valueRange = 80f..300f
    )
    SettingsSliderRow(
        title = "Long-word threshold",
        valueLabel = "${config.longWordChars} chars",
        value = config.longWordChars.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(longWordChars = newValue.toInt().coerceIn(8, 14)) }
        },
        valueRange = 8f..14f
    )

    Text("Difficulty model", style = MaterialTheme.typography.titleSmall)
    SettingsSliderRow(
        title = "Syllable boost",
        valueLabel = "+${config.syllableExtraMs}ms",
        value = config.syllableExtraMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(syllableExtraMs = newValue.toLong().coerceIn(0L, 45L)) }
        },
        valueRange = 0f..45f
    )
    SettingsSliderRow(
        title = "Rarity boost",
        valueLabel = "+${config.rarityExtraMaxMs}ms",
        value = config.rarityExtraMaxMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(rarityExtraMaxMs = newValue.toLong().coerceIn(0L, 200L)) }
        },
        valueRange = 0f..200f
    )
    SettingsSliderRow(
        title = "Complexity strength",
        valueLabel = "${(config.complexityStrength * 100).toInt()}%",
        value = (config.complexityStrength * 100).toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(complexityStrength = (newValue / 100.0).coerceIn(0.0, 1.0)) }
        },
        valueRange = 0f..100f
    )

    Text("Punctuation pauses", style = MaterialTheme.typography.titleSmall)
    SettingsSliderRow(
        title = "Comma",
        valueLabel = "${config.commaPauseMs}ms",
        value = config.commaPauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(commaPauseMs = newValue.toLong().coerceIn(0L, 260L)) }
        },
        valueRange = 0f..260f
    )
    SettingsSliderRow(
        title = "Dash",
        valueLabel = "${config.dashPauseMs}ms",
        value = config.dashPauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(dashPauseMs = newValue.toLong().coerceIn(0L, 320L)) }
        },
        valueRange = 0f..320f
    )
    SettingsSliderRow(
        title = "Semicolon",
        valueLabel = "${config.semicolonPauseMs}ms",
        value = config.semicolonPauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(semicolonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
        },
        valueRange = 0f..360f
    )
    SettingsSliderRow(
        title = "Colon",
        valueLabel = "${config.colonPauseMs}ms",
        value = config.colonPauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(colonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
        },
        valueRange = 0f..360f
    )
    SettingsSliderRow(
        title = "Sentence end",
        valueLabel = "${config.sentenceEndPauseMs}ms",
        value = config.sentenceEndPauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(sentenceEndPauseMs = newValue.toLong().coerceIn(0L, 500L)) }
        },
        valueRange = 0f..500f
    )
    SettingsSliderRow(
        title = "Parentheses",
        valueLabel = "${config.parenthesesPauseMs}ms",
        value = config.parenthesesPauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(parenthesesPauseMs = newValue.toLong().coerceIn(0L, 320L)) }
        },
        valueRange = 0f..320f
    )
    SettingsSliderRow(
        title = "Quotes",
        valueLabel = "${config.quotePauseMs}ms",
        value = config.quotePauseMs.toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(quotePauseMs = newValue.toLong().coerceIn(0L, 200L)) }
        },
        valueRange = 0f..200f
    )

    SettingsSliderRow(
        title = "Pause scaling",
        subtitle = "Compress pauses at high speed (floors still apply).",
        valueLabel = "${(config.pauseScaleExponent * 100).toInt()}%",
        value = (config.pauseScaleExponent * 100).toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(pauseScaleExponent = (newValue / 100.0).coerceIn(0.2, 0.9)) }
        },
        valueRange = 20f..90f
    )

    Text("Context shaping", style = MaterialTheme.typography.titleSmall)
    SettingsSliderRow(
        title = "Parentheticals",
        valueLabel = "+${((config.parentheticalMultiplier - 1.0) * 100).toInt()}%",
        value = ((config.parentheticalMultiplier - 1.0) * 100).toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(parentheticalMultiplier = (1.0 + newValue / 100.0).coerceIn(1.0, 1.35)) }
        },
        valueRange = 0f..35f
    )
    SettingsSliderRow(
        title = "Dialogue pace",
        valueLabel = "${(config.dialogueMultiplier * 100).toInt()}%",
        value = (config.dialogueMultiplier * 100).toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(dialogueMultiplier = (newValue / 100.0).coerceIn(0.85, 1.05)) }
        },
        valueRange = 85f..105f
    )

    Text("Rhythm", style = MaterialTheme.typography.titleSmall)
    SettingsSliderRow(
        title = "Stability",
        subtitle = "Higher = more responsive; lower = steadier cadence.",
        valueLabel = "${(config.smoothingAlpha * 100).toInt()}%",
        value = (config.smoothingAlpha * 100).toFloat(),
        onValueChange = { newValue ->
            updateConfig { it.copy(smoothingAlpha = (newValue / 100.0).coerceIn(0.0, 1.0)) }
        },
        valueRange = 0f..100f
    )

    SettingsSwitchRow(
        title = "Phrase chunking",
        subtitle = "Shows short 2-word units to reduce flicker.",
        checked = config.enablePhraseChunking,
        onCheckedChange = { enabled ->
            updateConfig { it.copy(enablePhraseChunking = enabled) }
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text("RSVP display", style = MaterialTheme.typography.titleMedium)

    SettingsSliderRow(
        title = "Font size",
        valueLabel = "${rsvpFontSizeSp.toInt()}sp",
        value = rsvpFontSizeSp,
        onValueChange = { onRsvpFontSizeChange(it.coerceIn(28f, 64f)) },
        valueRange = 28f..64f
    )

    SettingsSliderRow(
        title = "Text brightness",
        subtitle = "Dims the RSVP word display without changing the theme.",
        valueLabel = "${(rsvpTextBrightness.coerceIn(0.55f, 1.0f) * 100).toInt()}%",
        value = rsvpTextBrightness.coerceIn(0.55f, 1.0f),
        onValueChange = { onRsvpTextBrightnessChange(it.coerceIn(0.55f, 1.0f)) },
        valueRange = 0.55f..1.0f
    )

    RsvpFontFamilySelector(selected = rsvpFontFamily, onFontFamilyChange = onRsvpFontFamilyChange)
    RsvpFontWeightSelector(selected = rsvpFontWeight, onFontWeightChange = onRsvpFontWeightChange)

    SettingsSliderRow(
        title = "Vertical position",
        valueLabel = "${(rsvpVerticalBias * 100).toInt()}%",
        value = rsvpVerticalBias,
        onValueChange = onRsvpVerticalBiasChange,
        valueRange = -0.6f..0.6f
    )
    SettingsSliderRow(
        title = "Left bias",
        valueLabel = "${(rsvpHorizontalBias * 100).toInt()}%",
        value = rsvpHorizontalBias,
        onValueChange = onRsvpHorizontalBiasChange,
        valueRange = -0.6f..0.6f
    )
}

@Composable
private fun RsvpFontFamilySelector(
    selected: RsvpFontFamily,
    onFontFamilyChange: (RsvpFontFamily) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Font", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Weight", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
