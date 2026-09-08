package com.kairo.reader.data.preferences

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpConfigConstraints
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpCustomProfile
import org.json.JSONArray
import org.json.JSONObject

internal class RsvpProfileJsonCodec(private val onMalformed: (Throwable) -> Unit = {},) {
    fun parseCustomProfiles(raw: String?): List<RsvpCustomProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val obj = json.optJSONObject(i) ?: continue
                    val id = obj.optString("id").orEmpty()
                    val name = obj.optString("name").orEmpty()
                    if (!id.startsWith("user:") || name.isBlank()) continue
                    val updatedAt = obj.optLong("updatedAtMs", 0L)
                    val cfgObj = obj.optJSONObject("config") ?: JSONObject()
                    add(
                        RsvpCustomProfile(
                            id = id,
                            name = name,
                            config = decodeRsvpConfig(cfgObj),
                            updatedAtMs = updatedAt,
                        ),
                    )
                }
            }
        }.getOrElse { error ->
            onMalformed(error)
            emptyList()
        }
    }

    fun encodeCustomProfiles(profiles: List<RsvpCustomProfile>): String {
        val json = JSONArray()
        profiles.forEach { profile ->
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("updatedAtMs", profile.updatedAtMs)
            obj.put("config", encodeRsvpConfig(profile.config))
            json.put(obj)
        }
        return json.toString()
    }

    private fun encodeRsvpConfig(config: RsvpConfig): JSONObject =
        JSONObject().apply {
            putTiming(config)
            putWordFloors(config)
            putDifficulty(config)
            putLengthCurve(config)
            putChunking(config)
            putPunctuationPauses(config)
            putPauseScaling(config)
            putContextMultipliers(config)
            putRhythm(config)
            putProsody(config)
            putOrpAndDelays(config)
            putRamping(config)
            putAdaptiveTiming(config)
            putLegacyFields(config)
            putNaturalFlow(config)
            putBlink(config)
        }

    private fun JSONObject.putTiming(config: RsvpConfig) {
        put("tempoMsPerWord", config.tempoMsPerWord)
    }

    private fun JSONObject.putWordFloors(config: RsvpConfig) {
        put("minWordMs", config.minWordMs)
        put("longWordMinMs", config.longWordMinMs)
        put("longWordChars", config.longWordChars)
    }

    private fun JSONObject.putDifficulty(config: RsvpConfig) {
        put("syllableExtraMs", config.syllableExtraMs)
        put("rarityExtraMaxMs", config.rarityExtraMaxMs)
        put("complexityStrength", config.complexityStrength)
    }

    private fun JSONObject.putLengthCurve(config: RsvpConfig) {
        put("lengthStrength", config.lengthStrength)
        put("lengthExponent", config.lengthExponent)
    }

    private fun JSONObject.putChunking(config: RsvpConfig) {
        put("enablePhraseChunking", config.enablePhraseChunking)
        put("maxWordsPerUnit", config.maxWordsPerUnit)
        put("maxCharsPerUnit", config.maxCharsPerUnit)
        put("subwordChunkPauseMs", config.subwordChunkPauseMs)
        put("contextAssistMode", config.contextAssistMode.name)
        put("useRegressionAdaptivePacing", config.useRegressionAdaptivePacing)
    }

    private fun JSONObject.putPunctuationPauses(config: RsvpConfig) {
        put("commaPauseMs", config.commaPauseMs)
        put("periodPauseMs", config.periodPauseMs)
        put("semicolonPauseMs", config.semicolonPauseMs)
        put("colonPauseMs", config.colonPauseMs)
        put("dashPauseMs", config.dashPauseMs)
        put("parenthesesPauseMs", config.parenthesesPauseMs)
        put("quotePauseMs", config.quotePauseMs)
        put("sentenceEndPauseMs", config.sentenceEndPauseMs)
        put("paragraphPauseMs", config.paragraphPauseMs)
        put("paragraphPauseMultiplier", config.paragraphPauseMultiplier)
        put("pageBreakPauseMultiplier", config.pageBreakPauseMultiplier)
    }

    private fun JSONObject.putPauseScaling(config: RsvpConfig) {
        put("pauseScaleExponent", config.pauseScaleExponent)
        put("minPauseScale", config.minPauseScale)
        put("usePunctuationLandingHold", config.usePunctuationLandingHold)
    }

    private fun JSONObject.putContextMultipliers(config: RsvpConfig) {
        put("parentheticalMultiplier", config.parentheticalMultiplier)
        put("dialogueMultiplier", config.dialogueMultiplier)
    }

    private fun JSONObject.putRhythm(config: RsvpConfig) {
        put("smoothingAlpha", config.smoothingAlpha)
        put("maxSpeedupFactor", config.maxSpeedupFactor)
        put("maxSlowdownFactor", config.maxSlowdownFactor)
    }

    private fun JSONObject.putProsody(config: RsvpConfig) {
        put("useProsodyPacing", config.useProsodyPacing)
        put("prosodyStrength", config.prosodyStrength)
    }

    private fun JSONObject.putOrpAndDelays(config: RsvpConfig) {
        put("orpEnabled", config.orpEnabled)
        put("orpHighlightEnabled", config.orpHighlightEnabled)
        put("orpGuideEnabled", config.orpGuideEnabled)
        put("orpGuideBrightness", config.orpGuideBrightness)
        put("orpGuideThickness", config.orpGuideThickness)
        put("startDelayMs", config.startDelayMs)
        put("endDelayMs", config.endDelayMs)
    }

    private fun JSONObject.putRamping(config: RsvpConfig) {
        put("rampUpFrames", config.rampUpFrames)
        put("rampDownFrames", config.rampDownFrames)
    }

    private fun JSONObject.putAdaptiveTiming(config: RsvpConfig) {
        put("useAdaptiveTiming", config.useAdaptiveTiming)
        put("adaptiveDifficultyMaxHoldMs", config.adaptiveDifficultyMaxHoldMs)
        put("complexWordHoldMs", config.complexWordHoldMs)
        put("complexWordThreshold", config.complexWordThreshold)
    }

    private fun JSONObject.putLegacyFields(config: RsvpConfig) {
        put("maxChunkLength", config.maxChunkLength)
        put("punctuationPauseFactor", config.punctuationPauseFactor)
        put("useClausePausing", config.useClausePausing)
        put("clausePauseFactor", config.clausePauseFactor)
    }

    private fun JSONObject.putNaturalFlow(config: RsvpConfig) {
        put("useFocalStress", config.useFocalStress)
        put("focalSupportCompression", config.focalSupportCompression)
        put("useAnticipatoryLanding", config.useAnticipatoryLanding)
        put("anticipatoryLandingBoost", config.anticipatoryLandingBoost)
        put("dialoguePunctuationScale", config.dialoguePunctuationScale)
        put("useParentheticalAside", config.useParentheticalAside)
        put("parentheticalAsideMultiplier", config.parentheticalAsideMultiplier)
    }

    private fun JSONObject.putBlink(config: RsvpConfig) {
        put("blinkMode", config.blinkMode.name)
        put("blinkEnabled", config.blinkMode != BlinkMode.OFF)
    }

    private fun decodeRsvpConfig(obj: JSONObject): RsvpConfig {
        val defaults = RsvpConfig()
        val blinkModeRaw = obj.optString("blinkMode", "")
        val blinkMode =
            parseBlinkMode(blinkModeRaw.takeIf { it.isNotBlank() })
                ?: if (obj.optBoolean("blinkEnabled", false)) BlinkMode.SUBTLE else defaults.blinkMode
        return defaults
            .withTempoFromJson(obj, defaults)
            .withWordFloorsFromJson(obj, defaults)
            .withDifficultyFromJson(obj, defaults)
            .withLengthCurveFromJson(obj, defaults)
            .withChunkingFromJson(obj, defaults)
            .withPunctuationPausesFromJson(obj, defaults)
            .withPauseScalingFromJson(obj, defaults)
            .withContextMultipliersFromJson(obj, defaults)
            .withRhythmFromJson(obj, defaults)
            .withProsodyFromJson(obj, defaults)
            .withOrpAndDelaysFromJson(obj, defaults)
            .withRampingFromJson(obj, defaults)
            .withAdaptiveTimingFromJson(obj, defaults)
            .withLegacyFieldsFromJson(obj, defaults)
            .withNaturalFlowFromJson(obj, defaults)
            .withBlinkMode(blinkMode)
    }

    private fun RsvpConfig.withTempoFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(tempoMsPerWord = obj.optLong("tempoMsPerWord", defaults.tempoMsPerWord))

    private fun RsvpConfig.withWordFloorsFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            minWordMs = obj.optLong("minWordMs", defaults.minWordMs),
            longWordMinMs = obj.optLong("longWordMinMs", defaults.longWordMinMs),
            longWordChars = obj.optInt("longWordChars", defaults.longWordChars),
        )

    private fun RsvpConfig.withDifficultyFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            syllableExtraMs = obj.optLong("syllableExtraMs", defaults.syllableExtraMs),
            rarityExtraMaxMs = obj.optLong("rarityExtraMaxMs", defaults.rarityExtraMaxMs),
            complexityStrength = obj.optDouble("complexityStrength", defaults.complexityStrength),
        )

    private fun RsvpConfig.withLengthCurveFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            lengthStrength = obj.optDouble("lengthStrength", defaults.lengthStrength),
            lengthExponent = obj.optDouble("lengthExponent", defaults.lengthExponent),
        )

    private fun RsvpConfig.withChunkingFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            enablePhraseChunking =
            obj.optBoolean("enablePhraseChunking", defaults.enablePhraseChunking),
            maxWordsPerUnit = obj.optInt("maxWordsPerUnit", defaults.maxWordsPerUnit),
            maxCharsPerUnit = obj.optInt("maxCharsPerUnit", defaults.maxCharsPerUnit),
            subwordChunkPauseMs = obj.optLong("subwordChunkPauseMs", defaults.subwordChunkPauseMs),
            contextAssistMode =
            parseContextAssistMode(obj.optString("contextAssistMode", ""))
                ?: defaults.contextAssistMode,
            useRegressionAdaptivePacing =
            obj.optBoolean(
                "useRegressionAdaptivePacing",
                defaults.useRegressionAdaptivePacing,
            ),
        )

    private fun RsvpConfig.withPunctuationPausesFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            commaPauseMs = obj.optLong("commaPauseMs", defaults.commaPauseMs),
            periodPauseMs =
            if (obj.has("periodPauseMs")) {
                obj.optLong("periodPauseMs", defaults.periodPauseMs)
            } else {
                obj.optLong("sentenceEndPauseMs", defaults.sentenceEndPauseMs)
            },
            semicolonPauseMs = obj.optLong("semicolonPauseMs", defaults.semicolonPauseMs),
            colonPauseMs = obj.optLong("colonPauseMs", defaults.colonPauseMs),
            dashPauseMs = obj.optLong("dashPauseMs", defaults.dashPauseMs),
            parenthesesPauseMs =
            obj.optLong("parenthesesPauseMs", defaults.parenthesesPauseMs),
            quotePauseMs = obj.optLong("quotePauseMs", defaults.quotePauseMs),
            sentenceEndPauseMs =
            obj.optLong("sentenceEndPauseMs", defaults.sentenceEndPauseMs),
            paragraphPauseMs = obj.optLong("paragraphPauseMs", defaults.paragraphPauseMs),
            paragraphPauseMultiplier =
            obj.optDouble(
                "paragraphPauseMultiplier",
                defaults.paragraphPauseMultiplier,
            ),
            pageBreakPauseMultiplier =
            obj.optDouble(
                "pageBreakPauseMultiplier",
                defaults.pageBreakPauseMultiplier,
            ),
        )

    private fun RsvpConfig.withPauseScalingFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            pauseScaleExponent = obj.optDouble("pauseScaleExponent", defaults.pauseScaleExponent),
            minPauseScale = obj.optDouble("minPauseScale", defaults.minPauseScale),
            usePunctuationLandingHold =
            obj.optBoolean("usePunctuationLandingHold", defaults.usePunctuationLandingHold),
        )

    private fun RsvpConfig.withContextMultipliersFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            parentheticalMultiplier =
            obj.optDouble("parentheticalMultiplier", defaults.parentheticalMultiplier),
            dialogueMultiplier = obj.optDouble("dialogueMultiplier", defaults.dialogueMultiplier),
        )

    private fun RsvpConfig.withRhythmFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            smoothingAlpha = obj.optDouble("smoothingAlpha", defaults.smoothingAlpha),
            maxSpeedupFactor = obj.optDouble("maxSpeedupFactor", defaults.maxSpeedupFactor),
            maxSlowdownFactor = obj.optDouble("maxSlowdownFactor", defaults.maxSlowdownFactor),
        )

    private fun RsvpConfig.withProsodyFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            useProsodyPacing = obj.optBoolean("useProsodyPacing", defaults.useProsodyPacing),
            prosodyStrength =
            normalizeProsodyStrength(
                obj.optDouble("prosodyStrength", defaults.prosodyStrength),
                defaults.prosodyStrength,
            ),
        )

    private fun RsvpConfig.withOrpAndDelaysFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            orpEnabled = obj.optBoolean("orpEnabled", defaults.orpEnabled),
            orpHighlightEnabled =
            obj.optBoolean("orpHighlightEnabled", defaults.orpHighlightEnabled),
            orpGuideEnabled = obj.optBoolean("orpGuideEnabled", defaults.orpGuideEnabled),
            orpGuideBrightness = obj.optDouble("orpGuideBrightness", defaults.orpGuideBrightness),
            orpGuideThickness = obj.optDouble("orpGuideThickness", defaults.orpGuideThickness),
            startDelayMs = obj.optLong("startDelayMs", defaults.startDelayMs),
            endDelayMs = obj.optLong("endDelayMs", defaults.endDelayMs),
        )

    private fun RsvpConfig.withRampingFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            rampUpFrames = obj.optInt("rampUpFrames", defaults.rampUpFrames),
            rampDownFrames = obj.optInt("rampDownFrames", defaults.rampDownFrames),
        )

    private fun RsvpConfig.withAdaptiveTimingFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            useAdaptiveTiming = obj.optBoolean("useAdaptiveTiming", defaults.useAdaptiveTiming),
            adaptiveDifficultyMaxHoldMs =
            obj.optLong(
                "adaptiveDifficultyMaxHoldMs",
                defaults.adaptiveDifficultyMaxHoldMs,
            ),
            complexWordHoldMs = obj.optLong("complexWordHoldMs", defaults.complexWordHoldMs),
            complexWordThreshold =
            obj.optDouble("complexWordThreshold", defaults.complexWordThreshold),
        )

    private fun RsvpConfig.withLegacyFieldsFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            maxChunkLength = obj.optInt("maxChunkLength", defaults.maxChunkLength),
            punctuationPauseFactor =
            obj.optDouble("punctuationPauseFactor", defaults.punctuationPauseFactor),
            useClausePausing = obj.optBoolean("useClausePausing", defaults.useClausePausing),
            clausePauseFactor =
            normalizeClausePauseFactor(
                obj.optDouble("clausePauseFactor", defaults.clausePauseFactor),
                defaults.clausePauseFactor,
            ),
        )

    private fun RsvpConfig.withNaturalFlowFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            useFocalStress = obj.optBoolean("useFocalStress", defaults.useFocalStress),
            focalSupportCompression =
            obj.optDouble("focalSupportCompression", defaults.focalSupportCompression),
            useAnticipatoryLanding =
            obj.optBoolean("useAnticipatoryLanding", defaults.useAnticipatoryLanding),
            anticipatoryLandingBoost =
            obj.optDouble("anticipatoryLandingBoost", defaults.anticipatoryLandingBoost),
            dialoguePunctuationScale =
            obj.optDouble("dialoguePunctuationScale", defaults.dialoguePunctuationScale),
            useParentheticalAside =
            obj.optBoolean("useParentheticalAside", defaults.useParentheticalAside),
            parentheticalAsideMultiplier =
            obj.optDouble("parentheticalAsideMultiplier", defaults.parentheticalAsideMultiplier),
        ).normalizedNaturalFlowMultipliers(defaults)

    private fun normalizeClausePauseFactor(value: Double, fallback: Double): Double =
        (value.takeIf { it.isFinite() } ?: fallback).coerceIn(
            RsvpConfigConstraints.MIN_CLAUSE_PAUSE_FACTOR,
            RsvpConfigConstraints.MAX_CLAUSE_PAUSE_FACTOR,
        )

    private fun normalizeProsodyStrength(value: Double, fallback: Double): Double =
        (value.takeIf { it.isFinite() } ?: fallback).coerceIn(
            RsvpConfigConstraints.MIN_PROSODY_STRENGTH,
            RsvpConfigConstraints.MAX_PROSODY_STRENGTH,
        )

    private fun RsvpConfig.withBlinkMode(blinkMode: BlinkMode): RsvpConfig =
        copy(blinkMode = blinkMode)

    private fun parseBlinkMode(value: String?): BlinkMode? = value?.let {
        runCatching { BlinkMode.valueOf(it) }.getOrNull()
    }

    private fun parseContextAssistMode(value: String?): RsvpContextAssistMode? = value?.let {
        runCatching { RsvpContextAssistMode.valueOf(it) }.getOrNull()
    }
}
