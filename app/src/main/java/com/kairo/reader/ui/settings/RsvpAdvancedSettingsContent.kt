package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints as Constraints
import com.kairo.reader.core.rsvp.RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD
import com.kairo.reader.ui.rsvp.HORIZONTAL_BIAS_MAX
import com.kairo.reader.ui.rsvp.HORIZONTAL_BIAS_MIN
import com.kairo.reader.ui.rsvp.VERTICAL_BIAS_MAX
import com.kairo.reader.ui.rsvp.VERTICAL_BIAS_MIN

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun RsvpAdvancedSettingsContent(
    showAdvanced: Boolean,
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
) {
    val resources = LocalResources.current
    val config = state.config
    val tempoMsPerWord = state.tempoMsPerWord
    val unlockExtremeSpeed = state.unlockExtremeSpeed
    val rsvpFontFamily = state.fontFamily
    val rsvpFontWeight = state.fontWeight
    val rsvpVerticalBias = state.verticalBias
    val rsvpHorizontalBias = state.horizontalBias
    val onTempoMsPerWordChange = actions.onTempoMsPerWordChange
    val onUnlockExtremeSpeedChange = actions.onUnlockExtremeSpeedChange
    val onRsvpFontWeightChange = actions.onFontWeightChange
    val onRsvpFontFamilyChange = actions.onFontFamilyChange
    val onRsvpVerticalBiasChange = actions.onVerticalBiasChange
    val onRsvpHorizontalBiasChange = actions.onHorizontalBiasChange
    fun updateConfig(updater: (RsvpConfig) -> RsvpConfig) = actions.onConfigChange(updater)
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
                        if (!enabled && tempoMsPerWord < SAFE_MIN_TEMPO_MS_PER_WORD) {
                            onTempoMsPerWordChange(SAFE_MIN_TEMPO_MS_PER_WORD)
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
                    valueLabel = { resources.getString(R.string.format_chars, it.toInt()) },
                    rawValue = config.longWordChars.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                longWordChars =
                                newValue.toInt().coerceIn(
                                    Constraints.MIN_LONG_WORD_CHARS,
                                    Constraints.MAX_LONG_WORD_CHARS,
                                ),
                            )
                        }
                    },
                    valueRange =
                    Constraints.MIN_LONG_WORD_CHARS.toFloat()..Constraints.MAX_LONG_WORD_CHARS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_split_word_pause_title),
                    subtitle = stringResource(R.string.rsvp_split_word_pause_subtitle),
                    valueLabel = { resources.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.subwordChunkPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                subwordChunkPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_SUBWORD_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_SUBWORD_PAUSE_MS.toFloat(),
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_difficulty_model_title),
                summary =
                stringResource(
                    R.string.rsvp_difficulty_model_summary,
                    config.syllableExtraMs,
                    config.rarityExtraMaxMs,
                    formatPercent(resources, config.complexityStrength),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_syllable_boost_title),
                    valueLabel = { resources.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.syllableExtraMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                syllableExtraMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_SYLLABLE_EXTRA_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_SYLLABLE_EXTRA_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_rarity_boost_title),
                    valueLabel = { resources.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.rarityExtraMaxMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                rarityExtraMaxMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_RARITY_EXTRA_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_RARITY_EXTRA_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complexity_strength_title),
                    valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.complexityStrength * Constraints.PERCENT_SCALE).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                complexityStrength =
                                (newValue / Constraints.PERCENT_SCALE).coerceIn(0.0, 1.0),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.PERCENT_SCALE.toFloat(),
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_punctuation_pauses_title),
                summary =
                stringResource(
                    R.string.rsvp_punctuation_pauses_summary,
                    formatMultiplier(resources, config.punctuationPauseFactor),
                    config.commaPauseMs,
                    config.periodPauseMs,
                    config.paragraphPauseMs,
                    formatMultiplier(resources, config.pageBreakPauseMultiplier),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_breathing_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_breathing_subtitle),
                    valueLabel = { resources.getString(R.string.format_multiplier, it) },
                    rawValue = config.punctuationPauseFactor.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                punctuationPauseFactor =
                                newValue.toDouble().coerceIn(
                                    Constraints.MIN_PUNCTUATION_PAUSE_FACTOR,
                                    Constraints.MAX_PUNCTUATION_PAUSE_FACTOR,
                                ),
                            )
                        }
                    },
                    valueRange =
                    Constraints.MIN_PUNCTUATION_PAUSE_FACTOR.toFloat()..Constraints.MAX_PUNCTUATION_PAUSE_FACTOR.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_comma),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.commaPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                commaPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_COMMA_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_COMMA_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_period),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.periodPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                periodPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_PERIOD_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_PERIOD_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_dash),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.dashPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                dashPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_DASH_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_DASH_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_semicolon),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.semicolonPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                semicolonPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_SEMICOLON_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_SEMICOLON_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_colon),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.colonPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                colonPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_COLON_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_COLON_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_parentheses),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.parenthesesPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                parenthesesPauseMs =
                                newValue.toLong().coerceIn(
                                    0L,
                                    Constraints.MAX_PARENTHESES_PAUSE_MS,
                                ),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_PARENTHESES_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_quotes),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.quotePauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                quotePauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_QUOTE_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_QUOTE_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_paragraph),
                    valueLabel = { resources.getString(R.string.format_ms, it.toLong()) },
                    rawValue = config.paragraphPauseMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                paragraphPauseMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_PARAGRAPH_PAUSE_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_PARAGRAPH_PAUSE_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_paragraph_strength_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_paragraph_strength_subtitle),
                    valueLabel = { resources.getString(R.string.format_multiplier, it) },
                    rawValue = config.paragraphPauseMultiplier.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                paragraphPauseMultiplier =
                                newValue.toDouble().coerceIn(
                                    Constraints.MIN_PARAGRAPH_PAUSE_MULTIPLIER,
                                    Constraints.MAX_PARAGRAPH_PAUSE_MULTIPLIER,
                                ),
                            )
                        }
                    },
                    valueRange =
                    Constraints.MIN_PARAGRAPH_PAUSE_MULTIPLIER.toFloat()..Constraints.MAX_PARAGRAPH_PAUSE_MULTIPLIER.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_punctuation_page_break_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_page_break_subtitle),
                    valueLabel = { resources.getString(R.string.format_multiplier, it) },
                    rawValue = config.pageBreakPauseMultiplier.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                pageBreakPauseMultiplier =
                                newValue.toDouble().coerceIn(
                                    Constraints.MIN_PAGE_BREAK_PAUSE_MULTIPLIER,
                                    Constraints.MAX_PAGE_BREAK_PAUSE_MULTIPLIER,
                                ),
                            )
                        }
                    },
                    valueRange =
                    Constraints.MIN_PAGE_BREAK_PAUSE_MULTIPLIER.toFloat()..Constraints.MAX_PAGE_BREAK_PAUSE_MULTIPLIER.toFloat(),
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_pause_scaling_title),
                summary =
                stringResource(
                    R.string.rsvp_pause_scaling_summary,
                    formatPercent(resources, config.pauseScaleExponent),
                    formatPercent(resources, config.minPauseScale),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_scale_exponent_title),
                    subtitle = stringResource(R.string.rsvp_scale_exponent_subtitle),
                    valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.pauseScaleExponent * Constraints.PERCENT_SCALE).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                pauseScaleExponent =
                                (newValue / Constraints.PERCENT_SCALE).coerceIn(
                                    Constraints.MIN_PAUSE_SCALE_EXPONENT,
                                    Constraints.MAX_PAUSE_SCALE_EXPONENT,
                                ),
                            )
                        }
                    },
                    valueRange =
                    (Constraints.MIN_PAUSE_SCALE_EXPONENT * Constraints.PERCENT_SCALE)
                        .toFloat()..(Constraints.MAX_PAUSE_SCALE_EXPONENT * Constraints.PERCENT_SCALE).toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_minimum_scale_title),
                    subtitle = stringResource(R.string.rsvp_minimum_scale_subtitle),
                    valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.minPauseScale * Constraints.PERCENT_SCALE).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                minPauseScale =
                                (newValue / Constraints.PERCENT_SCALE).coerceIn(
                                    Constraints.MIN_PAUSE_SCALE,
                                    Constraints.MAX_PAUSE_SCALE,
                                ),
                            )
                        }
                    },
                    valueRange =
                    (Constraints.MIN_PAUSE_SCALE * Constraints.PERCENT_SCALE)
                        .toFloat()..(Constraints.MAX_PAUSE_SCALE * Constraints.PERCENT_SCALE).toFloat(),
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_context_shaping_title),
                summary =
                stringResource(
                    R.string.rsvp_context_shaping_summary,
                    formatDeltaPercent(resources, config.parentheticalMultiplier),
                    formatPercent(resources, config.dialogueMultiplier),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_parentheticals_title),
                    valueLabel = { resources.getString(R.string.format_plus_percent, it.toInt()) },
                    rawValue =
                    (
                        (config.parentheticalMultiplier - Constraints.MIN_PARENTHETICAL_MULTIPLIER) *
                            Constraints.PERCENT_SCALE
                        ).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                parentheticalMultiplier =
                                (
                                    Constraints.MIN_PARENTHETICAL_MULTIPLIER +
                                        newValue / Constraints.PERCENT_SCALE
                                    ).coerceIn(
                                    Constraints.MIN_PARENTHETICAL_MULTIPLIER,
                                    Constraints.MAX_PARENTHETICAL_MULTIPLIER,
                                ),
                            )
                        }
                    },
                    valueRange =
                    0f..(
                        (
                            Constraints.MAX_PARENTHETICAL_MULTIPLIER -
                                Constraints.MIN_PARENTHETICAL_MULTIPLIER
                            ) *
                            Constraints.PERCENT_SCALE
                        ).toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_dialogue_pace_title),
                    valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.dialogueMultiplier * Constraints.PERCENT_SCALE).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                dialogueMultiplier =
                                (newValue / Constraints.PERCENT_SCALE).coerceIn(
                                    Constraints.MIN_DIALOGUE_MULTIPLIER,
                                    Constraints.MAX_DIALOGUE_MULTIPLIER,
                                ),
                            )
                        }
                    },
                    valueRange =
                    (Constraints.MIN_DIALOGUE_MULTIPLIER * Constraints.PERCENT_SCALE)
                        .toFloat()..(Constraints.MAX_DIALOGUE_MULTIPLIER * Constraints.PERCENT_SCALE).toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_dialogue_punctuation_title),
                    subtitle = stringResource(R.string.rsvp_dialogue_punctuation_subtitle),
                    valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.dialoguePunctuationScale * Constraints.PERCENT_SCALE).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                dialoguePunctuationScale =
                                percentToMultiplier(
                                    newValue,
                                    minValue = Constraints.MIN_DIALOGUE_PUNCTUATION_SCALE,
                                    maxValue = Constraints.MAX_DIALOGUE_PUNCTUATION_SCALE,
                                ),
                            )
                        }
                    },
                    valueRange =
                    (Constraints.MIN_DIALOGUE_PUNCTUATION_SCALE * Constraints.PERCENT_SCALE)
                        .toFloat()..(Constraints.MAX_DIALOGUE_PUNCTUATION_SCALE * Constraints.PERCENT_SCALE).toFloat(),
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_parenthetical_aside_title),
                    subtitle = stringResource(R.string.rsvp_parenthetical_aside_subtitle),
                    checked = config.useParentheticalAside,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useParentheticalAside = enabled) }
                    },
                )
                if (config.useParentheticalAside) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_parenthetical_aside_pace_title),
                        subtitle = stringResource(R.string.rsvp_parenthetical_aside_pace_subtitle),
                        valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                        rawValue =
                        (config.parentheticalAsideMultiplier * Constraints.PERCENT_SCALE).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(
                                    parentheticalAsideMultiplier =
                                    percentToMultiplier(
                                        newValue,
                                        minValue = Constraints.MIN_PARENTHETICAL_ASIDE_MULTIPLIER,
                                        maxValue = Constraints.MAX_PARENTHETICAL_ASIDE_MULTIPLIER,
                                    ),
                                )
                            }
                        },
                        valueRange =
                        (
                            Constraints.MIN_PARENTHETICAL_ASIDE_MULTIPLIER *
                                Constraints.PERCENT_SCALE
                            ).toFloat()..(
                            Constraints.MAX_PARENTHETICAL_ASIDE_MULTIPLIER *
                                Constraints.PERCENT_SCALE
                            ).toFloat(),
                    )
                }
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_adaptive_pacing_title),
                summary =
                stringResource(
                    R.string.rsvp_adaptive_pacing_summary,
                    config.adaptiveDifficultyMaxHoldMs,
                    config.complexWordHoldMs,
                    formatMultiplier(resources, config.complexWordThreshold),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_difficulty_boost_title),
                    subtitle = stringResource(R.string.rsvp_difficulty_boost_subtitle),
                    valueLabel = { resources.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.adaptiveDifficultyMaxHoldMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                adaptiveDifficultyMaxHoldMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_ADAPTIVE_HOLD_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_ADAPTIVE_HOLD_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complex_word_boost_title),
                    subtitle = stringResource(R.string.rsvp_complex_word_boost_subtitle),
                    valueLabel = { resources.getString(R.string.format_plus_ms, it.toLong()) },
                    rawValue = config.complexWordHoldMs.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                complexWordHoldMs =
                                newValue.toLong().coerceIn(0L, Constraints.MAX_ADAPTIVE_HOLD_MS),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.MAX_ADAPTIVE_HOLD_MS.toFloat(),
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_complex_word_threshold_title),
                    subtitle = stringResource(R.string.rsvp_complex_word_threshold_subtitle),
                    valueLabel = { resources.getString(R.string.format_multiplier, it) },
                    rawValue = config.complexWordThreshold.toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                complexWordThreshold =
                                newValue.toDouble().coerceIn(
                                    Constraints.MIN_COMPLEX_WORD_THRESHOLD,
                                    Constraints.MAX_COMPLEX_WORD_THRESHOLD,
                                ),
                            )
                        }
                    },
                    valueRange =
                    Constraints.MIN_COMPLEX_WORD_THRESHOLD.toFloat()..Constraints.MAX_COMPLEX_WORD_THRESHOLD.toFloat(),
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_rhythm_title),
                summary =
                stringResource(
                    R.string.rsvp_rhythm_summary,
                    formatPercent(resources, config.smoothingAlpha),
                    formatDeltaPercent(resources, config.clausePauseFactor),
                    stringResource(blinkModeLabelRes(config.blinkMode)),
                ),
            ) {
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_stability_title),
                    subtitle = stringResource(R.string.rsvp_stability_subtitle),
                    valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                    rawValue = (config.smoothingAlpha * Constraints.PERCENT_SCALE).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                smoothingAlpha =
                                (newValue / Constraints.PERCENT_SCALE).coerceIn(0.0, 1.0),
                            )
                        }
                    },
                    valueRange = 0f..Constraints.PERCENT_SCALE.toFloat(),
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_focal_stress_title),
                    subtitle = stringResource(R.string.rsvp_focal_stress_subtitle),
                    checked = config.useFocalStress,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useFocalStress = enabled) }
                    },
                )

                if (config.useFocalStress) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_focal_support_title),
                        subtitle = stringResource(R.string.rsvp_focal_support_subtitle),
                        valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                        rawValue =
                        (config.focalSupportCompression * Constraints.PERCENT_SCALE).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(
                                    focalSupportCompression =
                                    percentToMultiplier(
                                        newValue,
                                        minValue = Constraints.MIN_FOCAL_SUPPORT_COMPRESSION,
                                        maxValue = Constraints.MAX_FOCAL_SUPPORT_COMPRESSION,
                                    ),
                                )
                            }
                        },
                        valueRange =
                        (Constraints.MIN_FOCAL_SUPPORT_COMPRESSION * Constraints.PERCENT_SCALE)
                            .toFloat()..(Constraints.MAX_FOCAL_SUPPORT_COMPRESSION * Constraints.PERCENT_SCALE)
                            .toFloat(),
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_anticipatory_landing_title),
                    subtitle = stringResource(R.string.rsvp_anticipatory_landing_subtitle),
                    checked = config.useAnticipatoryLanding,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useAnticipatoryLanding = enabled) }
                    },
                )

                if (config.useAnticipatoryLanding) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_anticipatory_landing_strength_title),
                        subtitle = stringResource(R.string.rsvp_anticipatory_landing_strength_subtitle),
                        valueLabel = { resources.getString(R.string.format_plus_percent, it.toInt()) },
                        rawValue =
                        (
                            (
                                config.anticipatoryLandingBoost -
                                    Constraints.MIN_ANTICIPATORY_LANDING_BOOST
                                ) *
                                Constraints.PERCENT_SCALE
                            ).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(
                                    anticipatoryLandingBoost =
                                    (
                                        Constraints.MIN_ANTICIPATORY_LANDING_BOOST +
                                            newValue / Constraints.PERCENT_SCALE
                                        ).coerceIn(
                                        Constraints.MIN_ANTICIPATORY_LANDING_BOOST,
                                        Constraints.MAX_ANTICIPATORY_LANDING_BOOST,
                                    ),
                                )
                            }
                        },
                        valueRange =
                        0f..(
                            (
                                Constraints.MAX_ANTICIPATORY_LANDING_BOOST -
                                    Constraints.MIN_ANTICIPATORY_LANDING_BOOST
                                ) *
                                Constraints.PERCENT_SCALE
                            ).toFloat(),
                    )
                }

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
                    valueLabel = { resources.getString(R.string.format_plus_percent, it.toInt()) },
                    rawValue =
                    (
                        (
                            config.clausePauseFactor.coerceIn(
                                Constraints.MIN_CLAUSE_PAUSE_FACTOR,
                                Constraints.MAX_CLAUSE_PAUSE_FACTOR,
                            ) - Constraints.MIN_CLAUSE_PAUSE_FACTOR
                            ) * Constraints.PERCENT_SCALE
                        ).toFloat(),
                    onCommit = { newValue ->
                        updateConfig {
                            it.copy(
                                clausePauseFactor =
                                (
                                    Constraints.MIN_CLAUSE_PAUSE_FACTOR +
                                        newValue / Constraints.PERCENT_SCALE
                                    ).coerceIn(
                                    Constraints.MIN_CLAUSE_PAUSE_FACTOR,
                                    Constraints.MAX_CLAUSE_PAUSE_FACTOR,
                                ),
                            )
                        }
                    },
                    valueRange =
                    0f..(
                        (
                            Constraints.MAX_CLAUSE_PAUSE_FACTOR -
                                Constraints.MIN_CLAUSE_PAUSE_FACTOR
                            ) *
                            Constraints.PERCENT_SCALE
                        ).toFloat(),
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_prosody_pacing_title),
                    subtitle = stringResource(R.string.rsvp_prosody_pacing_subtitle),
                    checked = config.useProsodyPacing,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(useProsodyPacing = enabled) }
                    },
                )

                if (config.useProsodyPacing) {
                    DeferredSliderRow(
                        title = stringResource(R.string.rsvp_prosody_strength_title),
                        subtitle = stringResource(R.string.rsvp_prosody_strength_subtitle),
                        valueLabel = { resources.getString(R.string.format_percent, it.toInt()) },
                        rawValue = (config.prosodyStrength * Constraints.PERCENT_SCALE).toFloat(),
                        onCommit = { newValue ->
                            updateConfig {
                                it.copy(
                                    prosodyStrength =
                                    (newValue / Constraints.PERCENT_SCALE).coerceIn(
                                        Constraints.MIN_PROSODY_STRENGTH,
                                        Constraints.MAX_PROSODY_STRENGTH,
                                    ),
                                )
                            }
                        },
                        valueRange =
                        (Constraints.MIN_PROSODY_STRENGTH * Constraints.PERCENT_SCALE)
                            .toFloat()..(Constraints.MAX_PROSODY_STRENGTH * Constraints.PERCENT_SCALE).toFloat(),
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.rsvp_punctuation_landing_title),
                    subtitle = stringResource(R.string.rsvp_punctuation_landing_subtitle),
                    checked = config.usePunctuationLandingHold,
                    onCheckedChange = { enabled ->
                        updateConfig { it.copy(usePunctuationLandingHold = enabled) }
                    },
                )

                BlinkModeSelector(
                    selected = config.blinkMode,
                    onSelect = { mode -> updateConfig { it.copy(blinkMode = mode) } },
                )
            }

            ExpandableSettingsSection(
                title = stringResource(R.string.rsvp_display_details_title),
                summary =
                stringResource(
                    R.string.rsvp_display_details_summary,
                    stringResource(rsvpFontFamilyLabelRes(rsvpFontFamily)),
                    stringResource(rsvpFontWeightLabelRes(rsvpFontWeight)),
                    formatBias(resources, rsvpVerticalBias),
                    formatBias(resources, rsvpHorizontalBias),
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
                    valueLabel = {
                        resources.getString(
                            R.string.format_percent,
                            (it * Constraints.PERCENT_SCALE).toInt(),
                        )
                    },
                    rawValue = rsvpVerticalBias,
                    onCommit = onRsvpVerticalBiasChange,
                    valueRange = VERTICAL_BIAS_MIN..VERTICAL_BIAS_MAX,
                )
                DeferredSliderRow(
                    title = stringResource(R.string.rsvp_left_bias_title),
                    valueLabel = {
                        resources.getString(
                            R.string.format_percent,
                            (it * Constraints.PERCENT_SCALE).toInt(),
                        )
                    },
                    rawValue = rsvpHorizontalBias,
                    onCommit = onRsvpHorizontalBiasChange,
                    valueRange = HORIZONTAL_BIAS_MIN..HORIZONTAL_BIAS_MAX,
                )
            }
        }
    }
}
