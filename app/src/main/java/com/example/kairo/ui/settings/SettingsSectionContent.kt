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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kairo.R
import com.example.kairo.core.model.BlinkMode
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpCustomProfile
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.RsvpProfile
import com.example.kairo.core.model.RsvpProfileIds
import com.example.kairo.core.rsvp.RsvpPaceEstimator
import com.example.kairo.ui.LocalDispatcherProvider
import kotlin.math.roundToInt
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
        title = stringResource(R.string.reader_font_size_title),
        valueLabel = stringResource(R.string.format_sp, fontSizeSp.toInt()),
        value = fontSizeSp,
        onValueChange = { onFontSizeChange(it.coerceIn(14f, 32f)) },
        valueRange = 14f..32f,
    )

    ThemeSelector(selected = readerTheme, onThemeChange = onThemeChange)

    SettingsSliderRow(
        title = stringResource(R.string.reader_text_brightness_title),
        subtitle = stringResource(R.string.reader_text_brightness_subtitle),
        valueLabel =
        stringResource(
            R.string.format_percent,
            (textBrightness.coerceIn(0.55f, 1.0f) * 100).toInt(),
        ),
        value = textBrightness.coerceIn(0.55f, 1.0f),
        onValueChange = { onTextBrightnessChange(it.coerceIn(0.55f, 1.0f)) },
        valueRange = 0.55f..1.0f,
    )

    Text(stringResource(R.string.reader_scrolling_title), style = MaterialTheme.typography.titleMedium)
    SettingsSwitchRow(
        title = stringResource(R.string.reader_invert_swipe_title),
        subtitle = stringResource(R.string.reader_invert_swipe_subtitle),
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
        title = stringResource(R.string.focus_enable_title),
        subtitle = stringResource(R.string.focus_mode_subtitle),
        checked = focusModeEnabled,
        onCheckedChange = onFocusModeEnabledChange,
    )

    SettingsSwitchRow(
        title = stringResource(R.string.focus_hide_status_bar_title),
        subtitle = stringResource(R.string.focus_hide_status_bar_subtitle),
        checked = focusHideStatusBar,
        onCheckedChange = onFocusHideStatusBarChange,
        enabled = focusModeEnabled,
    )

    SettingsSwitchRow(
        title = stringResource(R.string.focus_pause_notifications_title),
        subtitle = stringResource(R.string.focus_pause_notifications_subtitle),
        checked = focusPauseNotifications,
        onCheckedChange = onFocusPauseNotificationsChange,
        enabled = focusModeEnabled,
    )

    if (focusModeEnabled && focusPauseNotifications && !hasDndAccess) {
        Text(
            stringResource(R.string.focus_dnd_permission_message),
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
            Text(stringResource(R.string.focus_dnd_permission_action))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.focus_apply_title), style = MaterialTheme.typography.titleMedium)

    SettingsSwitchRow(
        title = stringResource(R.string.focus_apply_reader_title),
        subtitle = stringResource(R.string.focus_apply_reader_subtitle),
        checked = focusApplyInReader,
        onCheckedChange = onFocusApplyInReaderChange,
        enabled = focusModeEnabled,
    )
    SettingsSwitchRow(
        title = stringResource(R.string.focus_apply_rsvp_title),
        subtitle = stringResource(R.string.focus_apply_rsvp_subtitle),
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
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun AdvancedSettingsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "advanced-toggle")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_advanced_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.settings_advanced_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
    summary: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "section-toggle")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .animateContentSize()
                .padding(14.dp),
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

private fun formatPercent(context: Context, value: Double): String =
    context.getString(R.string.format_percent, (value * 100).roundToInt())

private fun formatDeltaPercent(context: Context, multiplier: Double): String {
    val delta = ((multiplier - 1.0) * 100).roundToInt()
    return context.getString(R.string.format_signed_percent, delta)
}

private fun formatMultiplier(context: Context, value: Double): String =
    context.getString(R.string.format_multiplier, value)

private fun formatBias(context: Context, value: Float): String =
    context.getString(R.string.format_percent, (value * 100).roundToInt())

private fun blinkModeLabelRes(mode: BlinkMode): Int =
    when (mode) {
        BlinkMode.OFF -> R.string.blink_mode_off
        BlinkMode.SUBTLE -> R.string.blink_mode_subtle
        BlinkMode.ADAPTIVE -> R.string.blink_mode_adaptive
    }

private fun blinkModeDescriptionRes(mode: BlinkMode): Int =
    when (mode) {
        BlinkMode.OFF -> R.string.blink_mode_off_description
        BlinkMode.SUBTLE -> R.string.blink_mode_subtle_description
        BlinkMode.ADAPTIVE -> R.string.blink_mode_adaptive_description
    }

private fun rsvpFontFamilyLabelRes(family: RsvpFontFamily): Int =
    when (family) {
        RsvpFontFamily.INTER -> R.string.rsvp_font_family_inter
        RsvpFontFamily.ROBOTO -> R.string.rsvp_font_family_roboto
    }

private fun rsvpFontWeightLabelRes(weight: RsvpFontWeight): Int =
    when (weight) {
        RsvpFontWeight.LIGHT -> R.string.rsvp_font_weight_light
        RsvpFontWeight.NORMAL -> R.string.rsvp_font_weight_normal
        RsvpFontWeight.MEDIUM -> R.string.rsvp_font_weight_medium
    }

private fun rsvpProfileNameRes(profile: RsvpProfile): Int =
    when (profile) {
        RsvpProfile.BALANCED -> R.string.rsvp_profile_balanced
        RsvpProfile.CHILL -> R.string.rsvp_profile_chill
        RsvpProfile.SPRINT -> R.string.rsvp_profile_sprint
        RsvpProfile.STUDY -> R.string.rsvp_profile_study
    }

private fun rsvpProfileDescriptionRes(profile: RsvpProfile): Int =
    when (profile) {
        RsvpProfile.BALANCED -> R.string.rsvp_profile_balanced_description
        RsvpProfile.CHILL -> R.string.rsvp_profile_chill_description
        RsvpProfile.SPRINT -> R.string.rsvp_profile_sprint_description
        RsvpProfile.STUDY -> R.string.rsvp_profile_study_description
    }

@Composable
private fun BlinkModeSelector(
    selected: BlinkMode,
    onSelect: (BlinkMode) -> Unit,
) {
    val options = listOf(BlinkMode.OFF, BlinkMode.SUBTLE, BlinkMode.ADAPTIVE)
    val subtitle = stringResource(blinkModeDescriptionRes(selected))

    Text(stringResource(R.string.blink_mode_title), style = MaterialTheme.typography.bodyLarge)
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
            val label = stringResource(blinkModeLabelRes(mode))

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
    val context = LocalContext.current
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
            stringResource(R.string.rsvp_estimated_pace, estimatedWpm)
        } else {
            stringResource(R.string.rsvp_estimating_pace)
        }
    Text(estimatedText, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(10.dp))

    val minTempoMs = if (unlockExtremeSpeed) 10L else 30L
    SettingsCard(
        title = stringResource(R.string.rsvp_quick_tune_title),
        subtitle = stringResource(R.string.rsvp_quick_tune_subtitle),
    ) {
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_tempo_title),
            subtitle = stringResource(R.string.rsvp_tempo_details_subtitle),
            valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
            rawValue = config.tempoMsPerWord.toFloat(),
            onCommit = { newValue ->
                updateConfig { it.copy(tempoMsPerWord = newValue.toLong().coerceIn(minTempoMs, 240L)) }
            },
            valueRange = minTempoMs.toFloat()..240f,
        )
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_min_word_time_title),
            subtitle = stringResource(R.string.rsvp_min_word_time_subtitle),
            valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
            rawValue = config.minWordMs.toFloat(),
            onCommit = { newValue ->
                updateConfig { it.copy(minWordMs = newValue.toLong().coerceIn(30L, 140L)) }
            },
            valueRange = 30f..140f,
        )
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_long_word_min_title),
            subtitle = stringResource(R.string.rsvp_long_word_min_subtitle),
            valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
            rawValue = config.longWordMinMs.toFloat(),
            onCommit = { newValue ->
                updateConfig { it.copy(longWordMinMs = newValue.toLong().coerceIn(80L, 300L)) }
            },
            valueRange = 80f..300f,
        )
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_sentence_end_pause_title),
            subtitle = stringResource(R.string.rsvp_sentence_end_pause_subtitle),
            valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
            rawValue = config.sentenceEndPauseMs.toFloat(),
            onCommit = { newValue ->
                updateConfig { it.copy(sentenceEndPauseMs = newValue.toLong().coerceIn(0L, 500L)) }
            },
            valueRange = 0f..500f,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_adaptive_pacing_title),
            subtitle = stringResource(R.string.rsvp_adaptive_pacing_subtitle),
            checked = config.useAdaptiveTiming,
            onCheckedChange = { enabled ->
                updateConfig { it.copy(useAdaptiveTiming = enabled) }
            },
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    SettingsCard(
        title = stringResource(R.string.rsvp_display_title),
        subtitle = stringResource(R.string.rsvp_display_subtitle),
    ) {
        DeferredSliderRow(
            title = stringResource(R.string.reader_font_size_title),
            valueLabel = { context.getString(R.string.format_sp, it.toInt()) },
            rawValue = rsvpFontSizeSp,
            onCommit = { onRsvpFontSizeChange(it.coerceIn(28f, 64f)) },
            valueRange = 28f..64f,
        )

        DeferredSliderRow(
            title = stringResource(R.string.reader_text_brightness_title),
            subtitle = stringResource(R.string.rsvp_text_brightness_subtitle),
            valueLabel = {
                context.getString(
                    R.string.format_percent,
                    (it.coerceIn(0.55f, 1.0f) * 100).toInt(),
                )
            },
            rawValue = rsvpTextBrightness.coerceIn(0.55f, 1.0f),
            onCommit = { onRsvpTextBrightnessChange(it.coerceIn(0.55f, 1.0f)) },
            valueRange = 0.55f..1.0f,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    AdvancedSettingsToggle(
        expanded = showAdvanced,
        onToggle = { showAdvanced = !showAdvanced },
    )

    if (showAdvanced) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_speed_limits_title),
                summary =
                stringResource(
                    if (unlockExtremeSpeed) {
                        R.string.rsvp_speed_limits_enabled
                    } else {
                        R.string.rsvp_speed_limits_disabled
                    },
                ),
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_unlock_extreme_speeds_title),
                    subtitle = stringResource(R.string.rsvp_unlock_extreme_speeds_subtitle_long),
                    checked = unlockExtremeSpeed,
                    onCheckedChange = { enabled ->
                        onUnlockExtremeSpeedChange(enabled)
                        if (!enabled && config.tempoMsPerWord < 30L) {
                            updateConfig { it.copy(tempoMsPerWord = 30L) }
                        }
                    },
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_readability_floors_title),
                summary =
                stringResource(
                    R.string.rsvp_readability_floors_summary,
                    config.longWordChars,
                    config.subwordChunkPauseMs,
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_long_word_threshold_title),
                    valueLabel = { context.getString(R.string.format_chars, it.toInt()) },
                    rawValue = config.longWordChars.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(longWordChars = newValue.toInt().coerceIn(8, 14)) }
                    },
                    valueRange = 8f..14f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_split_word_pause_title),
                    subtitle = stringResource(R.string.rsvp_split_word_pause_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.subwordChunkPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(subwordChunkPauseMs = newValue.toLong().coerceIn(0L, 200L))
                        }
                    },
                    valueRange = 0f..200f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_difficulty_model_title),
                summary =
                stringResource(
                    R.string.rsvp_difficulty_model_summary,
                    config.syllableExtraMs,
                    config.rarityExtraMaxMs,
                    formatPercent(context, config.complexityStrength),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_syllable_boost_title),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.syllableExtraMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(syllableExtraMs = newValue.toLong().coerceIn(0L, 45L)) }
                    },
                    valueRange = 0f..45f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_rarity_boost_title),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.rarityExtraMaxMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(rarityExtraMaxMs = newValue.toLong().coerceIn(0L, 200L)) }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complexity_strength_title),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.complexityStrength * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(complexityStrength = (newValue / 100.0).coerceIn(0.0, 1.0))
                        }
                    },
                    valueRange = 0f..100f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_punctuation_pauses_title),
                summary =
                stringResource(
                    R.string.rsvp_punctuation_pauses_summary,
                    config.commaPauseMs,
                    config.dashPauseMs,
                    config.paragraphPauseMs,
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_comma),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.commaPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(commaPauseMs = newValue.toLong().coerceIn(0L, 260L)) }
                    },
                    valueRange = 0f..260f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_dash),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.dashPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(dashPauseMs = newValue.toLong().coerceIn(0L, 320L)) }
                    },
                    valueRange = 0f..320f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_semicolon),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.semicolonPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(semicolonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
                    },
                    valueRange = 0f..360f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_colon),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.colonPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(colonPauseMs = newValue.toLong().coerceIn(0L, 360L)) }
                    },
                    valueRange = 0f..360f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_parentheses),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.parenthesesPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(parenthesesPauseMs = newValue.toLong().coerceIn(0L, 320L))
                        }
                    },
                    valueRange = 0f..320f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_quotes),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.quotePauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(quotePauseMs = newValue.toLong().coerceIn(0L, 200L)) }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_paragraph),
                    valueLabel = { context.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.paragraphPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(paragraphPauseMs = newValue.toLong().coerceIn(0L, 500L)) }
                    },
                    valueRange = 0f..500f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_pause_scaling_title),
                summary =
                stringResource(
                    R.string.rsvp_pause_scaling_summary,
                    formatPercent(context, config.pauseScaleExponent),
                    formatPercent(context, config.minPauseScale),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_scale_exponent_title),
                    subtitle = stringResource(R.string.rsvp_scale_exponent_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.pauseScaleExponent * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(pauseScaleExponent = (newValue / 100.0).coerceIn(0.2, 0.9))
                        }
                    },
                    valueRange = 20f..90f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_minimum_scale_title),
                    subtitle = stringResource(R.string.rsvp_minimum_scale_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.minPauseScale * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(minPauseScale = (newValue / 100.0).coerceIn(0.3, 1.0)) }
                    },
                    valueRange = 30f..100f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_context_shaping_title),
                summary =
                stringResource(
                    R.string.rsvp_context_shaping_summary,
                    formatDeltaPercent(context, config.parentheticalMultiplier),
                    formatPercent(context, config.dialogueMultiplier),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_parentheticals_title),
                    valueLabel = { context.getString(R.string.format_plus_percent, it.toInt()) },
                    rawValue = ((config.parentheticalMultiplier - 1.0) * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(parentheticalMultiplier = (1.0 + newValue / 100.0).coerceIn(1.0, 1.35))
                        }
                    },
                    valueRange = 0f..35f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_dialogue_pace_title),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.dialogueMultiplier * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(dialogueMultiplier = (newValue / 100.0).coerceIn(0.85, 1.05))
                        }
                    },
                    valueRange = 85f..105f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_adaptive_pacing_title),
                summary =
                stringResource(
                    R.string.rsvp_adaptive_pacing_summary,
                    config.adaptiveDifficultyMaxHoldMs,
                    config.complexWordHoldMs,
                    formatMultiplier(context, config.complexWordThreshold),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_difficulty_boost_title),
                    subtitle = stringResource(R.string.rsvp_difficulty_boost_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.adaptiveDifficultyMaxHoldMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(adaptiveDifficultyMaxHoldMs = newValue.toLong().coerceIn(0L, 200L))
                        }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complex_word_boost_title),
                    subtitle = stringResource(R.string.rsvp_complex_word_boost_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.complexWordHoldMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(complexWordHoldMs = newValue.toLong().coerceIn(0L, 200L)) }
                    },
                    valueRange = 0f..200f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complex_word_threshold_title),
                    subtitle = stringResource(R.string.rsvp_complex_word_threshold_subtitle),
                    valueLabel = { context.getString(R.string.format_multiplier, it) },
                    rawValue = config.complexWordThreshold.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(complexWordThreshold = newValue.toDouble().coerceIn(1.0, 1.6))
                        }
                    },
                    valueRange = 1f..1.6f,
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_rhythm_title),
                summary =
                stringResource(
                    R.string.rsvp_rhythm_summary,
                    formatPercent(context, config.smoothingAlpha),
                    formatDeltaPercent(context, config.clausePauseFactor),
                    stringResource(blinkModeLabelRes(config.blinkMode)),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_stability_title),
                    subtitle = stringResource(R.string.rsvp_stability_subtitle),
                    valueLabel = { context.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.smoothingAlpha * 100).toFloat(),
                    onCommit = { newValue ->
                        updateConfig { it.copy(smoothingAlpha = (newValue / 100.0).coerceIn(0.0, 1.0)) }
                    },
                    valueRange = 0f..100f,
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_clause_pacing_title),
                    subtitle = stringResource(R.string.rsvp_clause_pacing_subtitle),
                    checked = config.useClausePausing,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useClausePausing = enabled) }
                    },
                )

                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_clause_strength_title),
                    subtitle = stringResource(R.string.rsvp_clause_strength_subtitle),
                    valueLabel = { context.getString(R.string.format_plus_percent, it.toInt()) },
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
                    title = stringResource(R.string.rsvp_phrase_chunking_title),
                    subtitle = stringResource(R.string.rsvp_phrase_chunking_subtitle),
                    checked = config.enablePhraseChunking,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(enablePhraseChunking = enabled) }
                    },
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_display_details_title),
                summary =
                stringResource(
                    R.string.rsvp_display_details_summary,
                    stringResource(rsvpFontFamilyLabelRes(rsvpFontFamily)),
                    stringResource(rsvpFontWeightLabelRes(rsvpFontWeight)),
                    formatBias(context, rsvpVerticalBias),
                    formatBias(context, rsvpHorizontalBias),
                ),
            ) {
                RsvpFontFamilySelector(
                    selected = rsvpFontFamily,
                    onFontFamilyChange = onRsvpFontFamilyChange,
                )
                RsvpFontWeightSelector(
                    selected = rsvpFontWeight,
                    onFontWeightChange = onRsvpFontWeightChange,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_vertical_position_title),
                    valueLabel = { context.getString(R.string.format_percent, (it * 100).toInt()) },
                    rawValue = rsvpVerticalBias,
                    onCommit = onRsvpVerticalBiasChange,
                    valueRange = -0.6f..0.6f,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_left_bias_title),
                    valueLabel = { context.getString(R.string.format_percent, (it * 100).toInt()) },
                    rawValue = rsvpHorizontalBias,
                    onCommit = onRsvpHorizontalBiasChange,
                    valueRange = -0.6f..0.6f,
                )
            }
        }
    }
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
            selectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED ->
                stringResource(R.string.rsvp_profile_custom)
            selectedBuiltIn != null -> stringResource(rsvpProfileNameRes(selectedBuiltIn))
            selectedCustom != null -> selectedCustom.name
            else -> stringResource(R.string.rsvp_profile_custom)
        }
    val selectedDescription =
        when {
            selectedProfileId == RsvpProfileIds.CUSTOM_UNSAVED ->
                stringResource(R.string.rsvp_profile_unsaved_tweaks)
            selectedBuiltIn != null -> stringResource(rsvpProfileDescriptionRes(selectedBuiltIn))
            selectedCustom != null -> stringResource(R.string.rsvp_profile_saved_profile)
            else -> stringResource(R.string.rsvp_profile_unsaved_tweaks)
        }

    Text(stringResource(R.string.rsvp_profile_title), style = MaterialTheme.typography.titleMedium)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.rsvp_profile_label)) },
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
                        Text(
                            stringResource(R.string.rsvp_profile_custom),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.rsvp_profile_unsaved_tweaks),
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
                            Text(
                                stringResource(rsvpProfileNameRes(option)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(rsvpProfileDescriptionRes(option)),
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
                                    stringResource(R.string.rsvp_profile_saved_profile),
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
            Text(
                stringResource(
                    if (isCustomSelected) {
                        R.string.rsvp_profile_save_as
                    } else {
                        R.string.rsvp_profile_save_current
                    },
                ),
            )
        }

        if (isUserProfileSelected) {
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.rsvp_profile_save_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.rsvp_profile_name_label)) },
                    )
                    Text(
                        stringResource(R.string.rsvp_profile_save_description),
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
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteDialog && isUserProfileSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.rsvp_profile_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.rsvp_profile_delete_warning),
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
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
        Text(stringResource(R.string.rsvp_font_title), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RsvpFontFamily.entries.forEach { family ->
                OutlinedButton(
                    onClick = { onFontFamilyChange(family) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(rsvpFontFamilyLabelRes(family)),
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
        Text(stringResource(R.string.rsvp_weight_title), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RsvpFontWeight.entries.forEach { weight ->
                OutlinedButton(
                    onClick = { onFontWeightChange(weight) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(rsvpFontWeightLabelRes(weight)),
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
