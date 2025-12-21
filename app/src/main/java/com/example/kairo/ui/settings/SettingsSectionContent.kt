@file:Suppress(
    "AssignedValueIsNeverRead",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
)

package com.example.kairo.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kairo.core.model.BlinkMode
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpCustomProfile
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.RsvpProfile
import com.example.kairo.core.model.RsvpProfileIds
import com.example.kairo.core.model.description
import com.example.kairo.core.model.displayName
import com.example.kairo.core.rsvp.RsvpPaceEstimator
import com.example.kairo.ui.LocalDispatcherProvider
import kotlinx.coroutines.withContext

@Composable
fun ReaderSettingsContent(
    fontSizeSp: Float,
    readerTheme: ReaderTheme,
    textBrightness: Float,
    invertedScroll: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onTextBrightnessChange: (Float) -> Unit,
    onInvertedScrollChange: (Boolean) -> Unit,
) {
    SettingsSliderRow(
        title = "Font size",
        valueLabel = "${fontSizeSp.toInt()}sp",
        value = fontSizeSp,
        onValueChange = { onFontSizeChange(it.coerceIn(14f, 32f)) },
        valueRange = 14f..32f,
    )

    ThemeSelector(selected = readerTheme, onThemeChange = onThemeChange)

    SettingsSliderRow(
        title = "Text brightness",
        subtitle = "Dims the reader text without changing the theme.",
        valueLabel = "${(textBrightness.coerceIn(0.55f, 1.0f) * 100).toInt()}%",
        value = textBrightness.coerceIn(0.55f, 1.0f),
        onValueChange = { onTextBrightnessChange(it.coerceIn(0.55f, 1.0f)) },
        valueRange = 0.55f..1.0f,
    )

    Text("Scrolling", style = MaterialTheme.typography.titleMedium)
    SettingsSwitchRow(
        title = "Invert vertical swipe",
        subtitle = "Swipe up to move down, swipe down to move up.",
        checked = invertedScroll,
        onCheckedChange = onInvertedScrollChange,
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
    onFocusApplyInRsvpChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val hasDndAccess =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

    SettingsSwitchRow(
        title = "Enable focus mode",
        subtitle = "Hide system chrome while reading.",
        checked = focusModeEnabled,
        onCheckedChange = onFocusModeEnabledChange,
    )

    SettingsSwitchRow(
        title = "Hide status bar",
        subtitle = "Hides the top bar (time, notifications).",
        checked = focusHideStatusBar,
        onCheckedChange = onFocusHideStatusBarChange,
        enabled = focusModeEnabled,
    )

    SettingsSwitchRow(
        title = "Pause notifications",
        subtitle = "Uses Do Not Disturb while focus mode is active.",
        checked = focusPauseNotifications,
        onCheckedChange = onFocusPauseNotificationsChange,
        enabled = focusModeEnabled,
    )

    if (focusModeEnabled && focusPauseNotifications && !hasDndAccess) {
        Text(
            "Grant Do Not Disturb access to pause notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
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
        enabled = focusModeEnabled,
    )
    SettingsSwitchRow(
        title = "Apply in RSVP",
        subtitle = "Use focus mode in RSVP playback.",
        checked = focusApplyInRsvp,
        onCheckedChange = onFocusApplyInRsvpChange,
        enabled = focusModeEnabled,
    )
}

@Composable
private fun DeferredSliderRow(
    title: String,
    valueLabel: (Float) -> String,
    rawValue: Float,
    onCommit: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    subtitle: String? = null,
) {
    var localValue by remember { mutableFloatStateOf(rawValue) }
    LaunchedEffect(rawValue) {
        localValue = rawValue
    }

    val coercedValue = localValue.coerceIn(valueRange.start, valueRange.endInclusive)
    SettingsSliderRow(
        title = title,
        subtitle = subtitle,
        valueLabel = valueLabel(coercedValue),
        value = coercedValue,
        onValueChange = { localValue = it },
        onValueChangeFinished = { onCommit(coercedValue) },
        valueRange = valueRange,
    )
}

@Composable
private fun BlinkModeSelector(
    selected: BlinkMode,
    onSelect: (BlinkMode) -> Unit,
) {
    val options = listOf(BlinkMode.OFF, BlinkMode.SUBTLE, BlinkMode.ADAPTIVE)
    val subtitle =
        when (selected) {
            BlinkMode.OFF -> "No blink between words."
            BlinkMode.SUBTLE -> "Small, steady blink at higher speeds."
            BlinkMode.ADAPTIVE -> "Blink adapts to easier words and clean stretches."
        }

    Text("Blink mode", style = MaterialTheme.typography.bodyLarge)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.name }) { mode ->
            val isSelected = mode == selected
            val label =
                when (mode) {
                    BlinkMode.OFF -> "Off"
                    BlinkMode.SUBTLE -> "Subtle"
                    BlinkMode.ADAPTIVE -> "Adaptive"
                }

            Surface(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(mode) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (isSelected) 0.7f else 0.4f
                ),
                border =
                BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = 0.45f
                        )
                    },
                ),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun RsvpSettingsContent(
    selectedProfileId: String,
    customProfiles: List<RsvpCustomProfile>,
    config: RsvpConfig,
    unlockExtremeSpeed: Boolean,
    rsvpFontSizeSp: Float,
    rsvpTextBrightness: Float,
    rsvpFontFamily: RsvpFontFamily,
    rsvpFontWeight: RsvpFontWeight,
    rsvpVerticalBias: Float,
    rsvpHorizontalBias: Float,
    onSelectProfile: (String) -> Unit,
    onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
    onConfigChange: (RsvpConfig) -> Unit,
    onUnlockExtremeSpeedChange: (Boolean) -> Unit,
    onRsvpFontSizeChange: (Float) -> Unit,
    onRsvpTextBrightnessChange: (Float) -> Unit,
    onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit,
    onRsvpVerticalBiasChange: (Float) -> Unit,
    onRsvpHorizontalBiasChange: (Float) -> Unit,
) {
    fun updateConfig(updater: (RsvpConfig) -> RsvpConfig) {
        onConfigChange(updater(config))
    }

    RsvpProfileSelector(
        selectedProfileId = selectedProfileId,
        customProfiles = customProfiles,
        config = config,
        onSelectProfile = onSelectProfile,
        onSaveCustomProfile = onSaveCustomProfile,
        onDeleteCustomProfile = onDeleteCustomProfile,
    )

    var estimatedWpm by remember { mutableStateOf(0) }
    val dispatcherProvider = LocalDispatcherProvider.current
    LaunchedEffect(config) {
        estimatedWpm =
            withContext(dispatcherProvider.default) {
                RsvpPaceEstimator.estimateWpm(config)
            }
    }
    val estimatedText =
        if (estimatedWpm > 0) {
            "Estimated pace: $estimatedWpm WPM"
        } else {
            "Estimating pace..."
        }
    Text(estimatedText, style = MaterialTheme.typography.bodyMedium)

    SettingsSwitchRow(
        title = "Unlock extreme speeds",
        subtitle = "Allows very high speeds (can quickly become unreadable).",
        checked = unlockExtremeSpeed,
        onCheckedChange = { enabled ->
            onUnlockExtremeSpeedChange(enabled)
            if (!enabled && config.tempoMsPerWord < 30L) {
                updateConfig { it.copy(tempoMsPerWord = 30L) }
            }
        },
    )

    val minTempoMs = if (unlockExtremeSpeed) 10L else 30L
    DeferredSliderRow(
        title = "Tempo",
        subtitle = "Lower tempo = faster. This is the overall speed dial; everything else shapes readability.",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.tempoMsPerWord.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(tempoMsPerWord = newValue.toLong().coerceIn(minTempoMs, 240L)) }
        },
        valueRange = minTempoMs.toFloat()..240f,
    )

    Text("Readability floors", style = MaterialTheme.typography.titleSmall)
    DeferredSliderRow(
        title = "Minimum word time",
        subtitle = "Hard floor for any displayed word; prevents flashing at high speeds.",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.minWordMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(minWordMs = newValue.toLong().coerceIn(30L, 140L)) }
        },
        valueRange = 30f..140f,
    )
    DeferredSliderRow(
        title = "Long-word minimum",
        subtitle = "Ensures long/complex words feel readable even at very high speed.",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.longWordMinMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(longWordMinMs = newValue.toLong().coerceIn(80L, 300L)) }
        },
        valueRange = 80f..300f,
    )
    DeferredSliderRow(
        title = "Long-word threshold",
        valueLabel = { "${it.toInt()} chars" },
        rawValue = config.longWordChars.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(longWordChars = newValue.toInt().coerceIn(8, 14)) }
        },
        valueRange = 8f..14f,
    )

    Text("Difficulty model", style = MaterialTheme.typography.titleSmall)
    DeferredSliderRow(
        title = "Syllable boost",
        valueLabel = { "+${it.toLong()}ms" },
        rawValue = config.syllableExtraMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(syllableExtraMs = newValue.toLong().coerceIn(0L, 45L)) }
        },
        valueRange = 0f..45f,
    )
    DeferredSliderRow(
        title = "Rarity boost",
        valueLabel = { "+${it.toLong()}ms" },
        rawValue = config.rarityExtraMaxMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(rarityExtraMaxMs = newValue.toLong().coerceIn(0L, 200L)) }
        },
        valueRange = 0f..200f,
    )
    DeferredSliderRow(
        title = "Complexity strength",
        valueLabel = { "${it.toInt()}%" },
        rawValue = (config.complexityStrength * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(complexityStrength = (newValue / 100.0).coerceIn(0.0, 1.0)) }
        },
        valueRange = 0f..100f,
    )

    Text("Punctuation pauses", style = MaterialTheme.typography.titleSmall)
    DeferredSliderRow(
        title = "Comma",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.commaPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(commaPauseMs = newValue.toLong().coerceIn(0L, 260L)) }
        },
        valueRange = 0f..260f,
    )
    DeferredSliderRow(
        title = "Dash",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.dashPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(dashPauseMs = newValue.toLong().coerceIn(0L, 320L)) }
        },
        valueRange = 0f..320f,
    )
    DeferredSliderRow(
        title = "Semicolon",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.semicolonPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(semicolonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
        },
        valueRange = 0f..360f,
    )
    DeferredSliderRow(
        title = "Colon",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.colonPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(colonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
        },
        valueRange = 0f..360f,
    )
    DeferredSliderRow(
        title = "Sentence end",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.sentenceEndPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(sentenceEndPauseMs = newValue.toLong().coerceIn(0L, 500L)) }
        },
        valueRange = 0f..500f,
    )
    DeferredSliderRow(
        title = "Parentheses",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.parenthesesPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(parenthesesPauseMs = newValue.toLong().coerceIn(0L, 320L)) }
        },
        valueRange = 0f..320f,
    )
    DeferredSliderRow(
        title = "Quotes",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.quotePauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(quotePauseMs = newValue.toLong().coerceIn(0L, 200L)) }
        },
        valueRange = 0f..200f,
    )
    DeferredSliderRow(
        title = "Paragraph",
        valueLabel = { "${it.toLong()}ms" },
        rawValue = config.paragraphPauseMs.toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(paragraphPauseMs = newValue.toLong().coerceIn(0L, 500L)) }
        },
        valueRange = 0f..500f,
    )

    Text("Pause scaling", style = MaterialTheme.typography.titleSmall)
    DeferredSliderRow(
        title = "Scale exponent",
        subtitle = "Compress pauses at high speed (floors still apply).",
        valueLabel = { "${it.toInt()}%" },
        rawValue = (config.pauseScaleExponent * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(pauseScaleExponent = (newValue / 100.0).coerceIn(0.2, 0.9)) }
        },
        valueRange = 20f..90f,
    )
    DeferredSliderRow(
        title = "Minimum scale",
        subtitle = "Prevents pauses from vanishing at extreme speeds.",
        valueLabel = { "${it.toInt()}%" },
        rawValue = (config.minPauseScale * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(minPauseScale = (newValue / 100.0).coerceIn(0.3, 1.0)) }
        },
        valueRange = 30f..100f,
    )

    Text("Context shaping", style = MaterialTheme.typography.titleSmall)
    DeferredSliderRow(
        title = "Parentheticals",
        valueLabel = { "+${(it).toInt()}%" },
        rawValue = ((config.parentheticalMultiplier - 1.0) * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig {
                it.copy(parentheticalMultiplier = (1.0 + newValue / 100.0).coerceIn(1.0, 1.35))
            }
        },
        valueRange = 0f..35f,
    )
    DeferredSliderRow(
        title = "Dialogue pace",
        valueLabel = { "${it.toInt()}%" },
        rawValue = (config.dialogueMultiplier * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(dialogueMultiplier = (newValue / 100.0).coerceIn(0.85, 1.05)) }
        },
        valueRange = 85f..105f,
    )

    Text("Rhythm", style = MaterialTheme.typography.titleSmall)
    DeferredSliderRow(
        title = "Stability",
        subtitle = "Higher = more responsive; lower = steadier cadence.",
        valueLabel = { "${it.toInt()}%" },
        rawValue = (config.smoothingAlpha * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig { it.copy(smoothingAlpha = (newValue / 100.0).coerceIn(0.0, 1.0)) }
        },
        valueRange = 0f..100f,
    )

    SettingsSwitchRow(
        title = "Clause pacing",
        subtitle = "Adds tiny hesitations at clause starts for more natural phrasing.",
        checked = config.useClausePausing,
        onCheckedChange = { enabled ->
            updateConfig { it.copy(useClausePausing = enabled) }
        },
    )

    DeferredSliderRow(
        title = "Clause strength",
        subtitle = "Extra time at clause starts (only when clause pacing is enabled).",
        valueLabel = { "+${it.toInt()}%" },
        rawValue = ((config.clausePauseFactor.coerceIn(1.0, 1.6) - 1.0) * 100).toFloat(),
        onCommit = { newValue ->
            updateConfig {
                it.copy(clausePauseFactor = (1.0 + newValue / 100.0).coerceIn(1.0, 1.6))
            }
        },
        valueRange = 0f..60f,
    )

    BlinkModeSelector(
        selected = config.blinkMode,
        onSelect = { mode -> updateConfig { it.copy(blinkMode = mode) } },
    )

    SettingsSwitchRow(
        title = "Phrase chunking",
        subtitle = "Shows short 2-word units to reduce flicker.",
        checked = config.enablePhraseChunking,
        onCheckedChange = { enabled ->
            updateConfig { it.copy(enablePhraseChunking = enabled) }
        },
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text("RSVP display", style = MaterialTheme.typography.titleMedium)

    DeferredSliderRow(
        title = "Font size",
        valueLabel = { "${it.toInt()}sp" },
        rawValue = rsvpFontSizeSp,
        onCommit = { onRsvpFontSizeChange(it.coerceIn(28f, 64f)) },
        valueRange = 28f..64f,
    )

    DeferredSliderRow(
        title = "Text brightness",
        subtitle = "Dims the RSVP word display without changing the theme.",
        valueLabel = { "${(it.coerceIn(0.55f, 1.0f) * 100).toInt()}%" },
        rawValue = rsvpTextBrightness.coerceIn(0.55f, 1.0f),
        onCommit = { onRsvpTextBrightnessChange(it.coerceIn(0.55f, 1.0f)) },
        valueRange = 0.55f..1.0f,
    )

    RsvpFontFamilySelector(selected = rsvpFontFamily, onFontFamilyChange = onRsvpFontFamilyChange)
    RsvpFontWeightSelector(selected = rsvpFontWeight, onFontWeightChange = onRsvpFontWeightChange)

    DeferredSliderRow(
        title = "Vertical position",
        valueLabel = { "${(it * 100).toInt()}%" },
        rawValue = rsvpVerticalBias,
        onCommit = onRsvpVerticalBiasChange,
        valueRange = -0.6f..0.6f,
    )
    DeferredSliderRow(
        title = "Left bias",
        valueLabel = { "${(it * 100).toInt()}%" },
        rawValue = rsvpHorizontalBias,
        onCommit = onRsvpHorizontalBiasChange,
        valueRange = -0.6f..0.6f,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RsvpProfileSelector(
    selectedProfileId: String,
    customProfiles: List<RsvpCustomProfile>,
    config: RsvpConfig,
    onSelectProfile: (String) -> Unit,
    onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val builtInOptions = remember { RsvpProfile.entries.toList() }
    val selectedBuiltIn =
        remember(selectedProfileId) { RsvpProfileIds.parseBuiltIn(selectedProfileId) }
    val selectedCustom =
        remember(selectedProfileId, customProfiles) {
            customProfiles.firstOrNull { it.id == selectedProfileId }
        }
    val isCustomSelected =
        selectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED || selectedCustom != null
    val isUserProfileSelected = selectedCustom != null

    val selectedLabel =
        when {
            selectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED -> "Custom"
            selectedBuiltIn != null -> selectedBuiltIn.displayName()
            selectedCustom != null -> selectedCustom.name
            else -> "Custom"
        }
    val selectedDescription =
        when {
            selectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED -> "Unsaved tweaks"
            selectedBuiltIn != null -> selectedBuiltIn.description()
            selectedCustom != null -> "Saved profile"
            else -> "Unsaved tweaks"
        }

    Text("RSVP profile", style = MaterialTheme.typography.titleMedium)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Profile") },
            supportingText = { Text(selectedDescription) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
            Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Custom", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Unsaved tweaks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onSelectProfile(RsvpProfileIds.CUSTOM_UNSAVED)
                },
            )

            builtInOptions.forEach { option ->
                val optionId = RsvpProfileIds.builtIn(option)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.displayName(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectProfile(optionId)
                    },
                )
            }

            if (customProfiles.isNotEmpty()) {
                customProfiles.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Saved profile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectProfile(option.id)
                        },
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = {
                saveName = if (isUserProfileSelected) selectedCustom.name else ""
                showSaveDialog = true
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isCustomSelected) "Save as profile" else "Save current")
        }

        if (isUserProfileSelected) {
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        label = { Text("Profile name") },
                    )
                    Text(
                        "Saves the current RSVP settings as a named profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCustomProfile(saveName, config)
                        showSaveDialog = false
                        saveName = ""
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteDialog && isUserProfileSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete profile?") },
            text = {
                Text(
                    "This can’t be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomProfile(selectedProfileId)
                        showDeleteDialog = false
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RsvpFontFamilySelector(
    selected: RsvpFontFamily,
    onFontFamilyChange: (RsvpFontFamily) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Font", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RsvpFontFamily.entries.forEach { family ->
                OutlinedButton(
                    onClick = { onFontFamilyChange(family) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = family.name.lowercase().replaceFirstChar { it.titlecase() },
                        color = if (family ==
                            selected
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RsvpFontWeightSelector(
    selected: RsvpFontWeight,
    onFontWeightChange: (RsvpFontWeight) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Weight", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RsvpFontWeight.entries.forEach { weight ->
                OutlinedButton(
                    onClick = { onFontWeightChange(weight) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = weight.name.lowercase().replaceFirstChar { it.titlecase() },
                        color = if (weight ==
                            selected
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
            }
        }
    }
}
