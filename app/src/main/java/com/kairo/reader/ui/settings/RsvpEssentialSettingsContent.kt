package com.kairo.reader.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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

@Composable
internal fun RsvpReadingBasics(
    state: RsvpSettingsState,
    actions: RsvpSettingsActions,
    estimatedPace: String,
) {
    val resources = LocalResources.current
    val config = state.config
    val tempoMsPerWord = state.tempoMsPerWord
    val unlockExtremeSpeed = state.unlockExtremeSpeed
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
    SettingsCard(title = stringResource(R.string.rsvp_quick_tune_title)) {
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_reading_speed_title),
            subtitle = estimatedPace,
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
                actions.onTempoMsPerWordChange(
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
                actions.onConfigChange {
                    it.copy(
                        difficultWordSupport = (value / Constraints.PERCENT_SCALE)
                            .coerceIn(0.0, Constraints.MAX_DIFFICULT_WORD_SUPPORT)
                    )
                }
            },
            valueRange = 0f..(Constraints.MAX_DIFFICULT_WORD_SUPPORT * Constraints.PERCENT_SCALE).toFloat(),
        )
        RsvpWordsAtOnceSelector(config, actions.onConfigChange)
        DeferredSliderRow(
            title = stringResource(R.string.rsvp_text_size_title),
            valueLabel = { resources.getString(R.string.format_sp, it.toInt()) },
            rawValue = state.fontSizeSp,
            onCommit = { actions.onFontSizeChange(it.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)) },
            valueRange = MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP,
        )
    }
}

@Composable
internal fun RsvpReadingAids(config: RsvpConfig, actions: RsvpSettingsActions) {
    ExpandableSettingsSection(
        title = stringResource(R.string.rsvp_comprehension_title),
        summary = stringResource(R.string.rsvp_comprehension_subtitle),
    ) {
        ContextAssistModeSelector(
            selected = config.contextAssistMode,
            onSelect = { mode -> actions.onConfigChange { it.copy(contextAssistMode = mode) } },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_regression_pacing_title),
            subtitle = stringResource(R.string.rsvp_regression_pacing_subtitle),
            checked = config.useRegressionAdaptivePacing,
            onCheckedChange = { enabled ->
                actions.onConfigChange { it.copy(useRegressionAdaptivePacing = enabled) }
            },
        )
    }
}

@Composable
internal fun RsvpDisplaySettings(state: RsvpSettingsState, actions: RsvpSettingsActions) {
    val config = state.config
    val resources = LocalResources.current
    ExpandableSettingsSection(
        title = stringResource(R.string.rsvp_display_title),
        summary = stringResource(R.string.rsvp_display_subtitle),
    ) {
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
            state.textBrightness.coerceIn(
                Constraints.MIN_TEXT_BRIGHTNESS.toFloat(),
                Constraints.MAX_TEXT_BRIGHTNESS.toFloat(),
            ),
            onCommit = {
                actions.onTextBrightnessChange(
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
                actions.onConfigChange { it.copy(orpGuideEnabled = enabled) }
            },
        )

        SettingsSwitchRow(
            title = stringResource(R.string.rsvp_orp_highlight_title),
            subtitle = stringResource(R.string.rsvp_orp_highlight_subtitle),
            checked = config.orpHighlightEnabled,
            onCheckedChange = { enabled ->
                actions.onConfigChange { it.copy(orpHighlightEnabled = enabled) }
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
                    actions.onConfigChange {
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
                    actions.onConfigChange {
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
}
