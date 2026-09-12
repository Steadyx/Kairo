package com.kairo.reader.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.kairo.reader.core.model.BionicReadingPreferences
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.rsvp.RsvpSpeedControl

internal class UserPreferencesMapper(
    private val context: Context,
    private val keys: PrefKeys,
    private val profileJsonCodec: RsvpProfileJsonCodec,
    private val configCodec: RsvpConfigPreferenceCodec,
) {
    fun map(prefs: Preferences): UserPreferences {
        val defaults = systemDefaultUserPreferences()
        val customProfiles = profileJsonCodec.parseCustomProfiles(prefs[keys.customRsvpProfilesJson])
        val selectedProfileId = migrateAndReadSelectedProfileId(prefs, customProfiles)
        val configDefaults = configCodec.rsvpConfigDefaultsForProfile(selectedProfileId, customProfiles)
        val timingInfo = configCodec.readTimingInfo(prefs, configDefaults)
        val rsvpConfig = configCodec.readRsvpConfig(prefs, configDefaults)

        return defaults
            .withRsvpState(rsvpConfig, timingInfo.tempoMsPerWord, selectedProfileId, customProfiles)
            .withTutorialState(prefs, defaults)
            .withReaderSettings(prefs, defaults)
            .withTimedReadingMode(prefs, defaults)
            .withRsvpDisplaySettings(prefs, defaults, rsvpConfig)
            .withBionicDisplaySettings(prefs, defaults)
            .withFocusSettings(prefs, defaults)
            .withMomentumSettings(prefs, defaults)
    }

    private fun UserPreferences.withRsvpState(
        config: RsvpConfig,
        tempoMsPerWord: Long,
        selectedProfileId: String,
        customProfiles: List<RsvpCustomProfile>,
    ): UserPreferences =
        copy(
            rsvpConfig = config,
            rsvpTempoMsPerWord = tempoMsPerWord,
            rsvpSelectedProfileId = selectedProfileId,
            rsvpCustomProfiles = customProfiles,
        )

    private fun UserPreferences.withTutorialState(
        prefs: Preferences,
        defaults: UserPreferences,
    ): UserPreferences =
        copy(
            hasSeenStartingTutorial =
            prefs.readOrDefault(
                keys.hasSeenStartingTutorial,
                defaults.hasSeenStartingTutorial,
            ),
        )

    private fun UserPreferences.withReaderSettings(
        prefs: Preferences,
        defaults: UserPreferences,
    ): UserPreferences {
        val readerTheme = parseReaderTheme(prefs[keys.readerTheme], defaults.readerTheme)
        val readerTextBrightness =
            coerceTextBrightness(prefs[keys.readerTextBrightness], defaults.readerTextBrightness)
        return copy(
            readerFontSizeSp = prefs.readOrDefault(keys.readerFontSize, defaults.readerFontSizeSp),
            readerTheme = readerTheme,
            customTheme = com.kairo.reader.core.model.decodeCustomTheme(prefs[keys.customTheme]),
            readerFontFamily = RsvpFontFamily.entries.find { it.name == prefs[keys.readerFontFamily] } ?: defaults.readerFontFamily,
            interfaceFontFamily = RsvpFontFamily.entries.find {
                it.name == prefs[keys.interfaceFontFamily]
            } ?: defaults.interfaceFontFamily,
            readerTextBrightness = readerTextBrightness,
            invertedScroll = prefs.readOrDefault(keys.invertedScroll, defaults.invertedScroll),
        )
    }

    private fun UserPreferences.withTimedReadingMode(
        prefs: Preferences,
        defaults: UserPreferences,
    ): UserPreferences =
        copy(
            timedReadingMode =
            timedReadingModeFromStored(
                value = prefs[keys.timedReadingMode],
                fallback = defaults.timedReadingMode,
            ),
        )

    private fun UserPreferences.withRsvpDisplaySettings(
        prefs: Preferences,
        defaults: UserPreferences,
        rsvpConfig: RsvpConfig,
    ): UserPreferences {
        val rsvpTextBrightness =
            coerceTextBrightness(prefs[keys.rsvpTextBrightness], defaults.rsvpTextBrightness)
        val rsvpFontWeight =
            parseRsvpFontWeight(prefs[keys.rsvpFontWeight], defaults.rsvpFontWeight)
        val rsvpFontFamily =
            parseRsvpFontFamily(prefs[keys.rsvpFontFamily], defaults.rsvpFontFamily)
        val unlockExtremeSpeed =
            prefs[keys.unlockExtremeSpeed]
                ?: (rsvpConfig.tempoMsPerWord < RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD)
        return copy(
            rsvpFontSizeSp = prefs.readOrDefault(keys.rsvpFontSize, defaults.rsvpFontSizeSp),
            rsvpTextBrightness = rsvpTextBrightness,
            rsvpFontWeight = rsvpFontWeight,
            rsvpFontFamily = rsvpFontFamily,
            rsvpVerticalBias =
            prefs
                .readOrDefault(keys.rsvpVerticalBias, defaults.rsvpVerticalBias)
                .coerceIn(MIN_RSVP_VERTICAL_BIAS, MAX_RSVP_VERTICAL_BIAS),
            rsvpHorizontalBias =
            prefs
                .readOrDefault(keys.rsvpHorizontalBias, defaults.rsvpHorizontalBias)
                .coerceIn(MIN_RSVP_HORIZONTAL_BIAS, MAX_RSVP_HORIZONTAL_BIAS),
            rsvpPositioningGridEnabled =
            prefs.readOrDefault(
                keys.rsvpPositioningGridEnabled,
                defaults.rsvpPositioningGridEnabled,
            ),
            rsvpPositioningGridSnap =
            prefs.readOrDefault(keys.rsvpPositioningGridSnap, defaults.rsvpPositioningGridSnap),
            unlockExtremeSpeed = unlockExtremeSpeed,
        )
    }

    private fun UserPreferences.withBionicDisplaySettings(
        prefs: Preferences,
        defaults: UserPreferences,
    ): UserPreferences {
        val fallback = defaults.bionicReading
        return copy(
            bionicReading =
            BionicReadingPreferences(
                fixationStrength =
                prefs
                    .readOrDefault(keys.bionicFixationStrength, fallback.fixationStrength)
                    .coerceIn(
                        MIN_BIONIC_FIXATION_STRENGTH,
                        MAX_BIONIC_FIXATION_STRENGTH,
                    ),
                highlightStrength =
                prefs
                    .readOrDefault(keys.bionicHighlightStrength, fallback.highlightStrength)
                    .coerceIn(
                        MIN_BIONIC_HIGHLIGHT_STRENGTH,
                        MAX_BIONIC_HIGHLIGHT_STRENGTH,
                    ),
                fontSizeSp =
                prefs
                    .readOrDefault(keys.bionicFontSize, fallback.fontSizeSp)
                    .coerceIn(MIN_BIONIC_FONT_SIZE_SP, MAX_BIONIC_FONT_SIZE_SP),
                textBrightness =
                coerceTextBrightness(
                    prefs[keys.bionicTextBrightness],
                    fallback.textBrightness,
                ),
            ),
        )
    }

    private fun UserPreferences.withFocusSettings(
        prefs: Preferences,
        defaults: UserPreferences,
    ): UserPreferences =
        copy(
            focusModeEnabled = prefs.readOrDefault(keys.focusModeEnabled, defaults.focusModeEnabled),
            focusHideStatusBar =
            prefs.readOrDefault(keys.focusHideStatusBar, defaults.focusHideStatusBar),
            focusPauseNotifications =
            prefs.readOrDefault(
                keys.focusPauseNotifications,
                defaults.focusPauseNotifications,
            ),
            focusApplyInReader =
            prefs.readOrDefault(keys.focusApplyInReader, defaults.focusApplyInReader),
            focusApplyInRsvp =
            prefs.readOrDefault(keys.focusApplyInRsvp, defaults.focusApplyInRsvp),
        )

    private fun UserPreferences.withMomentumSettings(
        prefs: Preferences,
        defaults: UserPreferences,
    ): UserPreferences =
        copy(
            weeklyReadingGoalMinutes =
            normalizeWeeklyReadingGoalMinutes(
                value = prefs[keys.weeklyReadingGoalMinutes],
                fallback = defaults.weeklyReadingGoalMinutes,
            ),
            momentumResetCutoffAt =
            prefs.readOrDefault(
                keys.momentumResetCutoffAt,
                defaults.momentumResetCutoffAt,
            ),
        )

    private fun systemDefaultUserPreferences(): UserPreferences =
        UserPreferences(readerTheme = readerThemeForNightMode(context.resources.configuration.uiMode))

    private fun coerceTextBrightness(value: Float?, fallback: Float): Float =
        (value ?: fallback).coerceIn(MIN_TEXT_BRIGHTNESS, MAX_TEXT_BRIGHTNESS)

    private fun parseReaderTheme(value: String?, fallback: ReaderTheme): ReaderTheme =
        value?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: fallback

    private fun parseRsvpFontWeight(value: String?, fallback: RsvpFontWeight): RsvpFontWeight =
        value?.let { runCatching { RsvpFontWeight.valueOf(it) }.getOrNull() } ?: fallback

    private fun parseRsvpFontFamily(value: String?, fallback: RsvpFontFamily): RsvpFontFamily =
        value?.let { runCatching { RsvpFontFamily.valueOf(it) }.getOrNull() } ?: fallback

    private fun migrateAndReadSelectedProfileId(
        prefs: Preferences,
        customProfiles: List<RsvpCustomProfile>,
    ): String {
        val stored = prefs[keys.rsvpProfile]
        val normalized =
            if (stored == null) {
                RsvpProfileIds.builtIn(RsvpProfile.BALANCED)
            } else {
                normalizeRsvpProfileId(stored)
            }
        return when {
            normalized == RsvpProfileIds.CUSTOM_UNSAVED -> normalized
            RsvpProfileIds.isBuiltIn(normalized) -> normalized
            RsvpProfileIds.isCustom(normalized) && customProfiles.any { it.id == normalized } -> normalized
            else -> RsvpProfileIds.CUSTOM_UNSAVED
        }
    }
}

internal fun normalizeWeeklyReadingGoalMinutes(
    value: Int?,
    fallback: Int,
): Int =
    (value ?: fallback).coerceIn(
        MIN_WEEKLY_READING_GOAL_MINUTES,
        MAX_WEEKLY_READING_GOAL_MINUTES,
    )
