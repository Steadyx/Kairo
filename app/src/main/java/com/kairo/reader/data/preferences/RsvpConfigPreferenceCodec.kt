package com.kairo.reader.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints as Constraints
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.rsvp.MILLISECONDS_PER_MINUTE
import com.kairo.reader.core.rsvp.RsvpSpeedControl

internal class RsvpConfigPreferenceCodec(private val keys: PrefKeys, private val profileJsonCodec: RsvpProfileJsonCodec,) {
    fun writeRsvpConfig(
        prefs: MutablePreferences,
        config: RsvpConfig,
        includeTiming: Boolean = true,
    ) {
        val defaults = RsvpConfig()
        if (includeTiming) {
            writeTiming(prefs, config)
        }
        writeWordFloors(prefs, config)
        writeDifficulty(prefs, config)
        writeLengthCurve(prefs, config)
        writeChunking(prefs, config)
        writePunctuationPauses(prefs, config)
        writePauseScaling(prefs, config)
        writeContextMultipliers(prefs, config)
        writeRhythm(prefs, config)
        writeProsody(prefs, config, defaults)
        writeOrpAndDelays(prefs, config)
        writeRamping(prefs, config)
        writeAdaptiveTiming(prefs, config)
        writeLegacyFields(prefs, config, defaults)
        writeNaturalFlow(prefs, config)
        writeBlink(prefs, config)
    }

    private fun writeTiming(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.tempoMsPerWord] =
            config.tempoMsPerWord.coerceAtLeast(RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD)
        prefs[keys.rsvpSpeedCurveVersion] = RsvpSpeedControl.SPEED_CURVE_VERSION
        prefs.remove(legacyBaseWpmKey)
    }

    private fun writeWordFloors(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.minWordMs] = config.minWordMs
        prefs[keys.longWordMinMs] = config.longWordMinMs
        prefs[keys.longWordChars] = config.longWordChars
    }

    private fun writeDifficulty(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.syllableExtraMs] = config.syllableExtraMs
        prefs[keys.rarityExtraMaxMs] = config.rarityExtraMaxMs
        prefs[keys.complexityStrength] = config.complexityStrength
    }

    private fun writeLengthCurve(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.lengthStrength] = config.lengthStrength
        prefs[keys.lengthExponent] = config.lengthExponent
    }

    private fun writeChunking(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.enablePhraseChunking] = config.enablePhraseChunking
        prefs[keys.maxWordsPerUnit] = config.maxWordsPerUnit
        prefs[keys.maxCharsPerUnit] = config.maxCharsPerUnit
        prefs[keys.subwordChunkPauseMs] = config.subwordChunkPauseMs
        prefs[keys.contextAssistMode] = config.contextAssistMode.name
        prefs[keys.useRegressionAdaptivePacing] = config.useRegressionAdaptivePacing
    }

    fun writePunctuationPauses(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.commaPauseMs] = config.commaPauseMs
        prefs[keys.periodPauseMs] = config.periodPauseMs
        prefs[keys.semicolonPauseMs] = config.semicolonPauseMs
        prefs[keys.colonPauseMs] = config.colonPauseMs
        prefs[keys.dashPauseMs] = config.dashPauseMs
        prefs[keys.parenthesesPauseMs] = config.parenthesesPauseMs
        prefs[keys.quotePauseMs] = config.quotePauseMs
        prefs[keys.sentenceEndPauseMs] = config.sentenceEndPauseMs
        prefs[keys.paragraphPauseMs] = config.paragraphPauseMs
        prefs[keys.paragraphPauseMultiplier] = config.paragraphPauseMultiplier
        prefs[keys.pageBreakPauseMultiplier] = config.pageBreakPauseMultiplier
    }

    fun writePauseScaling(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.pauseScaleExponent] = config.pauseScaleExponent
        prefs[keys.minPauseScale] = config.minPauseScale
        prefs[keys.usePunctuationLandingHold] = config.usePunctuationLandingHold
    }

    private fun writeContextMultipliers(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.parentheticalMultiplier] = config.parentheticalMultiplier
        prefs[keys.dialogueMultiplier] = config.dialogueMultiplier
    }

    private fun writeRhythm(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.smoothingAlpha] = config.smoothingAlpha
        prefs[keys.maxSpeedupFactor] = config.maxSpeedupFactor
        prefs[keys.maxSlowdownFactor] = config.maxSlowdownFactor
    }

    private fun writeProsody(
        prefs: MutablePreferences,
        config: RsvpConfig,
        defaults: RsvpConfig,
    ) {
        prefs[keys.useProsodyPacing] = config.useProsodyPacing
        prefs[keys.prosodyStrength] =
            normalizeProsodyStrength(config.prosodyStrength, defaults.prosodyStrength)
    }

    private fun writeOrpAndDelays(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.orpEnabled] = config.orpEnabled
        prefs[keys.orpHighlightEnabled] = config.orpHighlightEnabled
        prefs[keys.orpGuideEnabled] = config.orpGuideEnabled
        prefs[keys.orpGuideBrightness] = config.orpGuideBrightness
        prefs[keys.orpGuideThickness] = config.orpGuideThickness
        prefs[keys.startDelayMs] = config.startDelayMs
        prefs[keys.endDelayMs] = config.endDelayMs
    }

    private fun writeRamping(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.rampUpFrames] = config.rampUpFrames
        prefs[keys.rampDownFrames] = config.rampDownFrames
    }

    private fun writeAdaptiveTiming(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.useAdaptiveTiming] = config.useAdaptiveTiming
        prefs[keys.adaptiveDifficultyMaxHoldMs] = config.adaptiveDifficultyMaxHoldMs
        prefs[keys.complexWordHoldMs] = config.complexWordHoldMs
        prefs[keys.complexWordThreshold] = config.complexWordThreshold
    }

    private fun writeLegacyFields(
        prefs: MutablePreferences,
        config: RsvpConfig,
        defaults: RsvpConfig,
    ) {
        prefs[keys.maxChunkLength] = config.maxChunkLength
        prefs[keys.punctuationPause] = config.punctuationPauseFactor
        prefs[keys.useClausePausing] = config.useClausePausing
        prefs[keys.clausePauseFactor] =
            normalizeClausePauseFactor(config.clausePauseFactor, defaults.clausePauseFactor)
    }

    private fun writeNaturalFlow(prefs: MutablePreferences, config: RsvpConfig) {
        val normalized = config.normalizedNaturalFlowMultipliers()
        prefs[keys.useFocalStress] = normalized.useFocalStress
        prefs[keys.focalSupportCompression] = normalized.focalSupportCompression
        prefs[keys.useAnticipatoryLanding] = normalized.useAnticipatoryLanding
        prefs[keys.anticipatoryLandingBoost] = normalized.anticipatoryLandingBoost
        prefs[keys.dialoguePunctuationScale] = normalized.dialoguePunctuationScale
        prefs[keys.useParentheticalAside] = normalized.useParentheticalAside
        prefs[keys.parentheticalAsideMultiplier] = normalized.parentheticalAsideMultiplier
    }

    private fun writeBlink(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.blinkMode] = config.blinkMode.name
        prefs[keys.blinkEnabled] = config.blinkMode != BlinkMode.OFF
    }

    data class TimingInfo(val tempoMsPerWord: Long, val baseWpm: Int,)

    fun readTimingInfo(prefs: Preferences, defaults: RsvpConfig): TimingInfo {
        val tempoMsPerWord =
            (
                prefs[keys.tempoMsPerWord]
                    ?: legacyWpmToTempoMs(
                        legacyWpm = prefs[legacyBaseWpmKey],
                        defaultTempoMs = defaults.tempoMsPerWord,
                    )
                ).coerceAtLeast(RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD)
        val baseWpm =
            (MILLISECONDS_PER_MINUTE / tempoMsPerWord.toDouble()).toInt().coerceAtLeast(1)
        return TimingInfo(tempoMsPerWord = tempoMsPerWord, baseWpm = baseWpm)
    }

    private fun readBlinkMode(prefs: Preferences, defaults: RsvpConfig): BlinkMode {
        val storedBlinkMode = parseBlinkMode(prefs[keys.blinkMode])
        return storedBlinkMode
            ?: if (prefs[keys.blinkEnabled] == true) {
                BlinkMode.SUBTLE
            } else {
                defaults.blinkMode
            }
    }

    fun readRsvpConfig(prefs: Preferences): RsvpConfig {
        val customProfiles = profileJsonCodec.parseCustomProfiles(prefs[keys.customRsvpProfilesJson])
        val selectedProfileId = migrateAndReadSelectedProfileId(prefs, customProfiles)
        return readRsvpConfig(
            prefs = prefs,
            defaults = rsvpConfigDefaultsForProfile(selectedProfileId, customProfiles),
        )
    }

    fun readRsvpConfig(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig {
        val timingInfo = readTimingInfo(prefs, defaults)
        val blinkMode = readBlinkMode(prefs, defaults)

        return defaults
            .withTiming(timingInfo)
            .withWordFloors(prefs, defaults)
            .withDifficulty(prefs, defaults)
            .withLengthCurve(prefs, defaults)
            .withChunking(prefs, defaults)
            .withPunctuationPauses(prefs, defaults)
            .withPauseScaling(prefs, defaults)
            .withContextMultipliers(prefs, defaults)
            .withRhythm(prefs, defaults)
            .withProsody(prefs, defaults)
            .withRamping(prefs, defaults)
            .withAdaptiveTiming(prefs, defaults)
            .withLegacyFields(prefs, defaults)
            .withNaturalFlow(prefs, defaults)
            .withOrpAndDelays(prefs, defaults)
            .withBlinkMode(blinkMode)
    }

    fun rsvpConfigDefaultsForProfile(
        selectedProfileId: String,
        customProfiles: List<RsvpCustomProfile>,
    ): RsvpConfig =
        when {
            RsvpProfileIds.isBuiltIn(selectedProfileId) ->
                RsvpProfileIds.parseBuiltIn(selectedProfileId)?.defaultConfig()
                    ?: RsvpProfile.BALANCED.defaultConfig()

            RsvpProfileIds.isCustom(selectedProfileId) ->
                customProfiles.firstOrNull { it.id == selectedProfileId }?.config
                    ?: RsvpProfile.BALANCED.defaultConfig()

            else -> RsvpProfile.BALANCED.defaultConfig()
        }

    private fun RsvpConfig.withTiming(timingInfo: TimingInfo): RsvpConfig =
        copy(
            tempoMsPerWord = timingInfo.tempoMsPerWord,
            baseWpm = timingInfo.baseWpm,
        )

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

    private fun RsvpConfig.withWordFloors(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            minWordMs = prefs.readOrDefault(keys.minWordMs, defaults.minWordMs),
            longWordMinMs = prefs.readOrDefault(keys.longWordMinMs, defaults.longWordMinMs),
            longWordChars = prefs.readOrDefault(keys.longWordChars, defaults.longWordChars),
        )

    private fun RsvpConfig.withDifficulty(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            syllableExtraMs = prefs.readOrDefault(keys.syllableExtraMs, defaults.syllableExtraMs),
            rarityExtraMaxMs =
            prefs.readOrDefault(keys.rarityExtraMaxMs, defaults.rarityExtraMaxMs),
            complexityStrength =
            prefs.readOrDefault(keys.complexityStrength, defaults.complexityStrength),
        )

    private fun RsvpConfig.withLengthCurve(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            lengthStrength = prefs.readOrDefault(keys.lengthStrength, defaults.lengthStrength),
            lengthExponent = prefs.readOrDefault(keys.lengthExponent, defaults.lengthExponent),
        )

    private fun RsvpConfig.withChunking(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            enablePhraseChunking =
            prefs.readOrDefault(keys.enablePhraseChunking, defaults.enablePhraseChunking),
            maxWordsPerUnit = prefs.readOrDefault(keys.maxWordsPerUnit, defaults.maxWordsPerUnit),
            maxCharsPerUnit = prefs.readOrDefault(keys.maxCharsPerUnit, defaults.maxCharsPerUnit),
            subwordChunkPauseMs =
            prefs.readOrDefault(keys.subwordChunkPauseMs, defaults.subwordChunkPauseMs),
            contextAssistMode =
            parseContextAssistMode(prefs[keys.contextAssistMode])
                ?: defaults.contextAssistMode,
            useRegressionAdaptivePacing =
            prefs.readOrDefault(
                keys.useRegressionAdaptivePacing,
                defaults.useRegressionAdaptivePacing,
            ),
        )

    private fun RsvpConfig.withPunctuationPauses(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            commaPauseMs = prefs.readOrDefault(keys.commaPauseMs, defaults.commaPauseMs),
            periodPauseMs =
            prefs[keys.periodPauseMs]
                ?: prefs.readOrDefault(keys.sentenceEndPauseMs, defaults.sentenceEndPauseMs),
            semicolonPauseMs =
            prefs.readOrDefault(keys.semicolonPauseMs, defaults.semicolonPauseMs),
            colonPauseMs = prefs.readOrDefault(keys.colonPauseMs, defaults.colonPauseMs),
            dashPauseMs = prefs.readOrDefault(keys.dashPauseMs, defaults.dashPauseMs),
            parenthesesPauseMs =
            prefs.readOrDefault(keys.parenthesesPauseMs, defaults.parenthesesPauseMs),
            quotePauseMs = prefs.readOrDefault(keys.quotePauseMs, defaults.quotePauseMs),
            sentenceEndPauseMs =
            prefs.readOrDefault(keys.sentenceEndPauseMs, defaults.sentenceEndPauseMs),
            paragraphPauseMs =
            prefs.readOrDefault(keys.paragraphPauseMs, defaults.paragraphPauseMs),
            paragraphPauseMultiplier =
            prefs.readOrDefault(
                keys.paragraphPauseMultiplier,
                defaults.paragraphPauseMultiplier,
            ),
            pageBreakPauseMultiplier =
            prefs.readOrDefault(
                keys.pageBreakPauseMultiplier,
                defaults.pageBreakPauseMultiplier,
            ),
        )

    private fun RsvpConfig.withPauseScaling(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            pauseScaleExponent =
            prefs.readOrDefault(keys.pauseScaleExponent, defaults.pauseScaleExponent),
            minPauseScale = prefs.readOrDefault(keys.minPauseScale, defaults.minPauseScale),
            usePunctuationLandingHold =
            prefs.readOrDefault(
                keys.usePunctuationLandingHold,
                defaults.usePunctuationLandingHold,
            ),
        )

    private fun RsvpConfig.withContextMultipliers(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            parentheticalMultiplier =
            prefs.readOrDefault(keys.parentheticalMultiplier, defaults.parentheticalMultiplier),
            dialogueMultiplier =
            prefs.readOrDefault(keys.dialogueMultiplier, defaults.dialogueMultiplier),
        )

    private fun RsvpConfig.withRhythm(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            smoothingAlpha = prefs.readOrDefault(keys.smoothingAlpha, defaults.smoothingAlpha),
            maxSpeedupFactor =
            prefs.readOrDefault(keys.maxSpeedupFactor, defaults.maxSpeedupFactor),
            maxSlowdownFactor =
            prefs.readOrDefault(keys.maxSlowdownFactor, defaults.maxSlowdownFactor),
        )

    private fun RsvpConfig.withProsody(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            useProsodyPacing =
            prefs.readOrDefault(keys.useProsodyPacing, defaults.useProsodyPacing),
            prosodyStrength =
            normalizeProsodyStrength(
                prefs[keys.prosodyStrength],
                defaults.prosodyStrength,
            ),
        )

    private fun RsvpConfig.withRamping(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            rampUpFrames = prefs.readOrDefault(keys.rampUpFrames, defaults.rampUpFrames),
            rampDownFrames = prefs.readOrDefault(keys.rampDownFrames, defaults.rampDownFrames),
        )

    private fun RsvpConfig.withAdaptiveTiming(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            useAdaptiveTiming =
            prefs.readOrDefault(keys.useAdaptiveTiming, defaults.useAdaptiveTiming),
            adaptiveDifficultyMaxHoldMs =
            prefs.readOrDefault(
                keys.adaptiveDifficultyMaxHoldMs,
                defaults.adaptiveDifficultyMaxHoldMs,
            ),
            complexWordHoldMs =
            prefs.readOrDefault(keys.complexWordHoldMs, defaults.complexWordHoldMs),
            complexWordThreshold =
            prefs.readOrDefault(keys.complexWordThreshold, defaults.complexWordThreshold),
        )

    private fun RsvpConfig.withLegacyFields(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            maxChunkLength = prefs.readOrDefault(keys.maxChunkLength, defaults.maxChunkLength),
            punctuationPauseFactor =
            prefs.readOrDefault(keys.punctuationPause, defaults.punctuationPauseFactor),
            useClausePausing =
            prefs.readOrDefault(keys.useClausePausing, defaults.useClausePausing),
            clausePauseFactor =
            normalizeClausePauseFactor(prefs[keys.clausePauseFactor], defaults.clausePauseFactor),
        )

    private fun RsvpConfig.withNaturalFlow(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            useFocalStress = prefs.readOrDefault(keys.useFocalStress, defaults.useFocalStress),
            focalSupportCompression =
            prefs.readOrDefault(
                keys.focalSupportCompression,
                defaults.focalSupportCompression,
            ),
            useAnticipatoryLanding =
            prefs.readOrDefault(keys.useAnticipatoryLanding, defaults.useAnticipatoryLanding),
            anticipatoryLandingBoost =
            prefs.readOrDefault(keys.anticipatoryLandingBoost, defaults.anticipatoryLandingBoost),
            dialoguePunctuationScale =
            prefs.readOrDefault(keys.dialoguePunctuationScale, defaults.dialoguePunctuationScale),
            useParentheticalAside =
            prefs.readOrDefault(keys.useParentheticalAside, defaults.useParentheticalAside),
            parentheticalAsideMultiplier =
            prefs.readOrDefault(
                keys.parentheticalAsideMultiplier,
                defaults.parentheticalAsideMultiplier,
            ),
        ).normalizedNaturalFlowMultipliers(defaults)

    private fun RsvpConfig.withOrpAndDelays(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            orpEnabled = prefs.readOrDefault(keys.orpEnabled, defaults.orpEnabled),
            orpHighlightEnabled =
            prefs.readOrDefault(keys.orpHighlightEnabled, defaults.orpHighlightEnabled),
            orpGuideEnabled =
            prefs.readOrDefault(keys.orpGuideEnabled, defaults.orpGuideEnabled),
            orpGuideBrightness =
            prefs.readOrDefault(keys.orpGuideBrightness, defaults.orpGuideBrightness),
            orpGuideThickness =
            prefs.readOrDefault(keys.orpGuideThickness, defaults.orpGuideThickness),
            startDelayMs = prefs.readOrDefault(keys.startDelayMs, defaults.startDelayMs),
            endDelayMs = prefs.readOrDefault(keys.endDelayMs, defaults.endDelayMs),
        )

    private fun RsvpConfig.withBlinkMode(blinkMode: BlinkMode): RsvpConfig =
        copy(blinkMode = blinkMode)

    private fun parseBlinkMode(value: String?): BlinkMode? = value?.let {
        runCatching { BlinkMode.valueOf(it) }.getOrNull()
    }

    private fun parseContextAssistMode(value: String?): RsvpContextAssistMode? = value?.let {
        runCatching { RsvpContextAssistMode.valueOf(it) }.getOrNull()
    }

    private fun normalizeClausePauseFactor(value: Double?, fallback: Double): Double =
        (value?.takeIf { it.isFinite() } ?: fallback).coerceIn(
            Constraints.MIN_CLAUSE_PAUSE_FACTOR,
            Constraints.MAX_CLAUSE_PAUSE_FACTOR,
        )

    private fun normalizeClausePauseFactor(value: Double, fallback: Double): Double =
        normalizeClausePauseFactor(value.takeIf { it.isFinite() }, fallback)

    private fun normalizeProsodyStrength(value: Double?, fallback: Double): Double =
        (value?.takeIf { it.isFinite() } ?: fallback).coerceIn(
            Constraints.MIN_PROSODY_STRENGTH,
            Constraints.MAX_PROSODY_STRENGTH,
        )

    private fun normalizeProsodyStrength(value: Double, fallback: Double): Double =
        normalizeProsodyStrength(value.takeIf { it.isFinite() }, fallback)
}
