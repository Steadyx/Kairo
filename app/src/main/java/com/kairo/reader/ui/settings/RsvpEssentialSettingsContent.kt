package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints as Constraints
import com.kairo.reader.core.rsvp.RsvpSpeedControl
import com.kairo.reader.core.rsvp.RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD
import com.kairo.reader.core.rsvp.RsvpSpeedControl.MAX_TEMPO_MS_PER_WORD
import com.kairo.reader.core.rsvp.RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD
import com.kairo.reader.ui.rsvp.MAX_FONT_SIZE_SP
import com.kairo.reader.ui.rsvp.MIN_FONT_SIZE_SP
import com.kairo.reader.ui.rsvp.rsvpSpeedBandLabelRes
import kotlin.math.roundToInt

// This is a declarative settings form whose repeated rows already delegate their behavior.
@Suppress("LongMethod")
@Composable
internal fun RsvpEssentialSettingsContent(
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
) {
    val resources = LocalResources.current
    val config = state.config
    val tempoMsPerWord = state.tempoMsPerWord
    val unlockExtremeSpeed = state.unlockExtremeSpeed
    val rsvpFontSizeSp = state.fontSizeSp
    val rsvpTextBrightness = state.textBrightness
    val onTempoMsPerWordChange = actions.onTempoMsPerWordChange
    val onUnlockExtremeSpeedChange = actions.onUnlockExtremeSpeedChange
    val onRsvpFontSizeChange = actions.onFontSizeChange
    val onRsvpTextBrightnessChange = actions.onTextBrightnessChange
    fun updateConfig(updater: (RsvpConfig) -> RsvpConfig) = actions.onConfigChange(updater)
    val minTempoMs = if (unlockExtremeSpeed) EXTREME_MIN_TEMPO_MS_PER_WORD else SAFE_MIN_TEMPO_MS_PER_WORD
    val speedPercent =
        remember(tempoMsPerWord, minTempoMs) {
            RsvpSpeedControl.displaySpeed(
                RsvpSpeedControl.speedForTempoMs(
                    tempoMsPerWord = tempoMsPerWord,
                    minTempoMsPerWord = minTempoMs,
                    maxTempoMsPerWord = MAX_TEMPO_MS_PER_WORD,
                ),
            )
        }
    SettingsCard(
        title = stringResource(R.string.rsvp_quick_tune_title),
        subtitle = stringResource(R.string.rsvp_quick_tune_subtitle),
    ) {
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_reading_speed_title),
            subtitle = stringResource(R.string.rsvp_reading_speed_details_subtitle),
            valueLabel = {
                resources.getString(
                    R.string.rsvp_reading_speed_indicator,
                    resources.getString(
                        rsvpSpeedBandLabelRes(
                            tempoMsPerWord =
                            RsvpSpeedControl.tempoForSpeed(
                                speed = it,
                                minTempoMsPerWord = minTempoMs,
                                maxTempoMsPerWord = MAX_TEMPO_MS_PER_WORD,
                            ),
                            extremeUnlocked = unlockExtremeSpeed,
                        ),
                    ),
                    it.roundToInt(),
                )
            },
            rawValue = speedPercent.toFloat(),
            onCommit = { newValue ->
                onTempoMsPerWordChange(
                    RsvpSpeedControl.tempoForSpeed(
                        speed = newValue,
                        minTempoMsPerWord = minTempoMs,
                        maxTempoMsPerWord = MAX_TEMPO_MS_PER_WORD,
                    ),
                )
            },
            valueRange = RsvpSpeedControl.MIN_SPEED..RsvpSpeedControl.MAX_SPEED,
        )
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_difficult_word_support_title),
            subtitle = stringResource(R.string.rsvp_difficult_word_support_subtitle),
            valueLabel = {
                when (it.toInt()) {
                    0 -> resources.getString(R.string.rsvp_reading_support_off)
                    200 -> resources.getString(R.string.rsvp_reading_support_strong)
                    else -> resources.getString(R.string.format_percent, it.toInt())
                }
            },
            rawValue = (config.difficultWordSupport * Constraints.PERCENT_SCALE).toFloat(),
            onCommit = { value ->
                updateConfig {
                    it.copy(
                        difficultWordSupport = (value / Constraints.PERCENT_SCALE)
                            .coerceIn(0.0, Constraints.MAX_DIFFICULT_WORD_SUPPORT)
                    )
                }
            },
            valueRange = 0f..(Constraints.MAX_DIFFICULT_WORD_SUPPORT * Constraints.PERCENT_SCALE).toFloat(),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    ExpandableSettingsSection(
        title = stringResource(R.string.rsvp_comprehension_title),
        summary = stringResource(R.string.rsvp_comprehension_subtitle),
    ) {
        ContextAssistModeSelector(
            selected = config.contextAssistMode,
            onSelect = { mode -> updateConfig { it.copy(contextAssistMode = mode) } },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_phrase_chunking_title),
            subtitle = stringResource(R.string.rsvp_phrase_chunking_subtitle),
            checked = config.enablePhraseChunking,
            onCheckedChange = { enabled ->
                updateConfig {
                    it.copy(
                        enablePhraseChunking = enabled,
                        maxWordsPerUnit =
                        if (enabled) {
                            coercePhraseChunkWordLimit(it.maxWordsPerUnit)
                        } else {
                            it.maxWordsPerUnit
                        },
                    )
                }
            },
        )
        if (config.enablePhraseChunking) {
            DeferredIntegerSliderRow(
                title = stringResource(R.string.rsvp_phrase_chunk_size_title),
                subtitle = stringResource(R.string.rsvp_phrase_chunk_size_subtitle),
                valueLabel = { resources.getString(R.string.format_words, it) },
                rawValue = config.maxWordsPerUnit,
                onCommit = { wordLimit ->
                    updateConfig {
                        it.copy(maxWordsPerUnit = coercePhraseChunkWordLimit(wordLimit))
                    }
                },
                valueRange = PHRASE_CHUNK_MIN_WORDS..PHRASE_CHUNK_MAX_WORDS,
            )
        }
        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_regression_pacing_title),
            subtitle = stringResource(R.string.rsvp_regression_pacing_subtitle),
            checked = config.useRegressionAdaptivePacing,
            onCheckedChange = { enabled ->
                updateConfig { it.copy(useRegressionAdaptivePacing = enabled) }
            },
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    ExpandableSettingsSection(
        title = stringResource(R.string.rsvp_display_title),
        summary = stringResource(R.string.rsvp_display_subtitle),
    ) {
        DeferredSliderRow(
            title = stringResource(R.string.reader_font_size_title),
            valueLabel = { resources.getString(R.string.format_sp, it.toInt()) },
            rawValue = rsvpFontSizeSp,
            onCommit = { onRsvpFontSizeChange(it.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)) },
            valueRange = MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP,
        )

        DeferredSliderRow(
            title = stringResource(R.string.reader_text_brightness_title),
            subtitle = stringResource(R.string.rsvp_text_brightness_subtitle),
            valueLabel = {
                resources.getString(
                    R.string.format_percent,
                    (
                        it.coerceIn(
                            Constraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                            Constraints.MAX_TEXT_BRIGHTNESS.toFloat(),
                        ) * Constraints.PERCENT_SCALE
                        ).toInt(),
                )
            },
            rawValue =
            rsvpTextBrightness.coerceIn(
                Constraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                Constraints.MAX_TEXT_BRIGHTNESS.toFloat(),
            ),
            onCommit = {
                onRsvpTextBrightnessChange(
                    it.coerceIn(
                        Constraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                        Constraints.MAX_TEXT_BRIGHTNESS.toFloat(),
                    ),
                )
            },
            valueRange =
            Constraints.MIN_TEXT_BRIGHTNESS.toFloat()..Constraints.MAX_TEXT_BRIGHTNESS.toFloat(),
        )

        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_orp_guide_title),
            subtitle = stringResource(R.string.rsvp_orp_guide_subtitle),
            checked = config.orpGuideEnabled,
            onCheckedChange = { enabled ->
                updateConfig { it.copy(orpGuideEnabled = enabled) }
            },
        )

        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_orp_highlight_title),
            subtitle = stringResource(R.string.rsvp_orp_highlight_subtitle),
            checked = config.orpHighlightEnabled,
            onCheckedChange = { enabled ->
                updateConfig { it.copy(orpHighlightEnabled = enabled) }
            },
        )

        if (config.orpGuideEnabled) {
            DeferredSliderRow(
                title = stringResource(R.string.rsvp_orp_guide_brightness_title),
                subtitle = stringResource(R.string.rsvp_orp_guide_brightness_subtitle),
                valueLabel = {
                    resources.getString(
                        R.string.format_percent,
                        it.coerceIn(
                            (Constraints.MIN_ORP_GUIDE_BRIGHTNESS * Constraints.PERCENT_SCALE).toFloat(),
                            (Constraints.MAX_ORP_GUIDE_BRIGHTNESS * Constraints.PERCENT_SCALE).toFloat(),
                        ).toInt(),
                    )
                },
                rawValue = (config.orpGuideBrightness * Constraints.PERCENT_SCALE).toFloat(),
                onCommit = { newValue ->
                    updateConfig {
                        it.copy(
                            orpGuideBrightness =
                            (newValue / Constraints.PERCENT_SCALE).coerceIn(
                                Constraints.MIN_ORP_GUIDE_BRIGHTNESS,
                                Constraints.MAX_ORP_GUIDE_BRIGHTNESS,
                            ),
                        )
                    }
                },
                valueRange =
                (Constraints.MIN_ORP_GUIDE_BRIGHTNESS * Constraints.PERCENT_SCALE)
                    .toFloat()..(Constraints.MAX_ORP_GUIDE_BRIGHTNESS * Constraints.PERCENT_SCALE).toFloat(),
            )
            DeferredSliderRow(
                title = stringResource(R.string.rsvp_orp_guide_thickness_title),
                subtitle = stringResource(R.string.rsvp_orp_guide_thickness_subtitle),
                valueLabel = { resources.getString(R.string.format_multiplier, it) },
                rawValue = config.orpGuideThickness.toFloat(),
                onCommit = { newValue ->
                    updateConfig {
                        it.copy(
                            orpGuideThickness =
                            newValue.toDouble().coerceIn(
                                Constraints.MIN_ORP_GUIDE_THICKNESS,
                                Constraints.MAX_ORP_GUIDE_THICKNESS,
                            ),
                        )
                    }
                },
                valueRange =
                Constraints.MIN_ORP_GUIDE_THICKNESS.toFloat()..Constraints.MAX_ORP_GUIDE_THICKNESS.toFloat(),
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
}
