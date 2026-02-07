@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "TooManyFunctions",
)

package com.example.kairo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kairo.core.model.BlinkMode
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpCustomProfile
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.RsvpProfile
import com.example.kairo.core.model.RsvpProfileIds
import com.example.kairo.core.model.UserPreferences
import com.example.kairo.core.model.defaultConfig
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import org.json.JSONObject

private val legacyBaseWpmKey = intPreferencesKey("base_wpm")
private const val MIN_TEXT_BRIGHTNESS = 0.55f
private const val MAX_TEXT_BRIGHTNESS = 1.0f

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

private fun <T> Preferences.readOrDefault(key: Preferences.Key<T>, fallback: T): T =
    this[key] ?: fallback

class PreferencesRepositoryImpl(private val context: Context,) : PreferencesRepository {
    private val keys = PrefKeys

    override val preferences: Flow<UserPreferences> =
        context.dataStore.data
            .onEach { prefs -> migrateLegacyBaseWpmIfNeeded(prefs) }
            .map { prefs -> buildUserPreferences(prefs) }

    private suspend fun migrateLegacyBaseWpmIfNeeded(prefs: Preferences) {
        if (!prefs.contains(legacyBaseWpmKey)) return
        context.dataStore.edit { mutable ->
            if (!mutable.contains(keys.tempoMsPerWord)) {
                val tempoMs =
                    legacyWpmToTempoMs(
                        legacyWpm = mutable[legacyBaseWpmKey],
                        defaultTempoMs = RsvpConfig().tempoMsPerWord,
                    )
                mutable[keys.tempoMsPerWord] = tempoMs
            }
            mutable.remove(legacyBaseWpmKey)
        }
    }

    private fun buildUserPreferences(prefs: Preferences): UserPreferences {
        val defaults = UserPreferences()
        val customProfiles = parseCustomProfiles(prefs[keys.customRsvpProfilesJson])
        val selectedProfileId = migrateAndReadSelectedProfileId(prefs, customProfiles)
        val rsvpConfig = readRsvpConfig(prefs)

        return defaults
            .withRsvpState(rsvpConfig, selectedProfileId, customProfiles)
            .withReaderSettings(prefs, defaults)
            .withRsvpDisplaySettings(prefs, defaults, rsvpConfig)
            .withFocusSettings(prefs, defaults)
    }

    private fun UserPreferences.withRsvpState(
        config: RsvpConfig,
        selectedProfileId: String,
        customProfiles: List<RsvpCustomProfile>,
    ): UserPreferences =
        copy(
            rsvpConfig = config,
            rsvpSelectedProfileId = selectedProfileId,
            rsvpCustomProfiles = customProfiles,
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
            readerTextBrightness = readerTextBrightness,
            invertedScroll = prefs.readOrDefault(keys.invertedScroll, defaults.invertedScroll),
        )
    }

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
            prefs[keys.unlockExtremeSpeed] ?: (rsvpConfig.tempoMsPerWord < 30L)
        return copy(
            rsvpFontSizeSp = prefs.readOrDefault(keys.rsvpFontSize, defaults.rsvpFontSizeSp),
            rsvpTextBrightness = rsvpTextBrightness,
            rsvpFontWeight = rsvpFontWeight,
            rsvpFontFamily = rsvpFontFamily,
            rsvpVerticalBias = prefs.readOrDefault(keys.rsvpVerticalBias, defaults.rsvpVerticalBias),
            rsvpHorizontalBias =
                prefs.readOrDefault(keys.rsvpHorizontalBias, defaults.rsvpHorizontalBias),
            unlockExtremeSpeed = unlockExtremeSpeed,
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

    override suspend fun updateRsvpConfig(updater: (RsvpConfig) -> RsvpConfig) {
        context.dataStore.edit { prefs ->
            val updated = updater(readRsvpConfig(prefs))
            prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
            writeRsvpConfig(prefs, updated)
        }
    }

    override suspend fun selectRsvpProfile(profileId: String) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeProfileId(profileId)
            when {
                normalized == RsvpProfileIds.CUSTOM_UNSAVED -> {
                    prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
                }

                RsvpProfileIds.isBuiltIn(normalized) -> {
                    val builtIn = RsvpProfileIds.parseBuiltIn(normalized) ?: RsvpProfile.BALANCED
                    prefs[keys.rsvpProfile] = RsvpProfileIds.builtIn(builtIn)
                    writeRsvpConfig(prefs, builtIn.defaultConfig())
                }

                RsvpProfileIds.isCustom(normalized) -> {
                    val profiles = parseCustomProfiles(prefs[keys.customRsvpProfilesJson])
                    val match = profiles.firstOrNull { it.id == normalized }
                    prefs[keys.rsvpProfile] = normalized
                    if (match != null) {
                        writeRsvpConfig(prefs, match.config)
                    } else {
                        prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
                    }
                }

                else -> {
                    prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
                }
            }
        }
    }

    override suspend fun saveRsvpCustomProfile(
        name: String,
        config: RsvpConfig,
    ) {
        val trimmedName = name.trim().take(32)
        if (trimmedName.isBlank()) return

        context.dataStore.edit { prefs ->
            val existing = parseCustomProfiles(prefs[keys.customRsvpProfilesJson]).toMutableList()
            val id = "user:${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            existing.add(
                RsvpCustomProfile(
                    id = id,
                    name = trimmedName,
                    config = config,
                    updatedAtMs = now,
                ),
            )
            prefs[keys.customRsvpProfilesJson] = encodeCustomProfiles(existing)
            prefs[keys.rsvpProfile] = id
        }
    }

    override suspend fun deleteRsvpCustomProfile(profileId: String) {
        if (!RsvpProfileIds.isCustom(profileId)) return
        context.dataStore.edit { prefs ->
            val existing = parseCustomProfiles(prefs[keys.customRsvpProfilesJson]).toMutableList()
            val removed = existing.removeAll { it.id == profileId }
            if (!removed) return@edit
            prefs[keys.customRsvpProfilesJson] = encodeCustomProfiles(existing)
            if (prefs[keys.rsvpProfile] == profileId) {
                prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
            }
        }
    }

    override suspend fun updateUnlockExtremeSpeed(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.unlockExtremeSpeed] = enabled }
    }

    override suspend fun updateFontSize(size: Float) {
        context.dataStore.edit { prefs -> prefs[keys.readerFontSize] = size }
    }

    override suspend fun updateReaderTextBrightness(brightness: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.readerTextBrightness] = brightness.coerceIn(
                MIN_TEXT_BRIGHTNESS,
                MAX_TEXT_BRIGHTNESS,
            )
        }
    }

    override suspend fun updateTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[keys.readerTheme] = theme }
    }

    override suspend fun updateInvertedScroll(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.invertedScroll] = enabled }
    }

    override suspend fun updateRsvpFontSize(size: Float) {
        context.dataStore.edit { prefs -> prefs[keys.rsvpFontSize] = size }
    }

    override suspend fun updateRsvpTextBrightness(brightness: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.rsvpTextBrightness] = brightness.coerceIn(
                MIN_TEXT_BRIGHTNESS,
                MAX_TEXT_BRIGHTNESS,
            )
        }
    }

    override suspend fun updateRsvpFontWeight(weight: RsvpFontWeight) {
        context.dataStore.edit { prefs -> prefs[keys.rsvpFontWeight] = weight.name }
    }

    override suspend fun updateRsvpFontFamily(family: RsvpFontFamily) {
        context.dataStore.edit { prefs -> prefs[keys.rsvpFontFamily] = family.name }
    }

    override suspend fun updateRsvpVerticalBias(bias: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.rsvpVerticalBias] = bias.coerceIn(-0.7f, 0.7f)
        }
    }

    override suspend fun updateRsvpHorizontalBias(bias: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.rsvpHorizontalBias] = bias.coerceIn(-0.7f, 0.7f)
        }
    }

    override suspend fun updateFocusModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.focusModeEnabled] = enabled }
    }

    override suspend fun updateFocusHideStatusBar(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.focusHideStatusBar] = enabled }
    }

    override suspend fun updateFocusPauseNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.focusPauseNotifications] = enabled }
    }

    override suspend fun updateFocusApplyInReader(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.focusApplyInReader] = enabled }
    }

    override suspend fun updateFocusApplyInRsvp(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.focusApplyInRsvp] = enabled }
    }

    override suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }

    private fun legacyWpmToTempoMs(legacyWpm: Int?, defaultTempoMs: Long): Long =
        when {
            legacyWpm == null -> defaultTempoMs
            legacyWpm <= 0 -> defaultTempoMs
            else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(10L)
        }

    private fun normalizeClausePauseFactor(value: Double?, fallback: Double): Double {
        val normalized = value?.takeIf { it.isFinite() } ?: fallback
        return normalized.coerceIn(1.0, 1.6)
    }

    private fun normalizeClausePauseFactor(value: Double, fallback: Double): Double =
        normalizeClausePauseFactor(value.takeIf { it.isFinite() }, fallback)

    private fun normalizeProsodyStrength(value: Double?, fallback: Double): Double {
        val normalized = value?.takeIf { it.isFinite() } ?: fallback
        return normalized.coerceIn(0.0, 1.6)
    }

    private fun normalizeProsodyStrength(value: Double, fallback: Double): Double =
        normalizeProsodyStrength(value.takeIf { it.isFinite() }, fallback)

    private fun coerceTextBrightness(value: Float?, fallback: Float): Float =
        (value ?: fallback).coerceIn(MIN_TEXT_BRIGHTNESS, MAX_TEXT_BRIGHTNESS)

    private fun parseReaderTheme(value: String?, fallback: ReaderTheme): ReaderTheme =
        value?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: fallback

    private fun parseRsvpFontWeight(value: String?, fallback: RsvpFontWeight): RsvpFontWeight =
        value?.let { runCatching { RsvpFontWeight.valueOf(it) }.getOrNull() } ?: fallback

    private fun parseRsvpFontFamily(value: String?, fallback: RsvpFontFamily): RsvpFontFamily =
        value?.let { runCatching { RsvpFontFamily.valueOf(it) }.getOrNull() } ?: fallback

    private fun normalizeProfileId(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return RsvpProfileIds.CUSTOM_UNSAVED
        if (trimmed == "CUSTOM") return RsvpProfileIds.CUSTOM_UNSAVED
        runCatching { RsvpProfile.valueOf(trimmed) }.getOrNull()?.let { parsed ->
            return RsvpProfileIds.builtIn(parsed)
        }
        if (trimmed.startsWith("builtin:") ||
            trimmed.startsWith("user:") ||
            trimmed == RsvpProfileIds.CUSTOM_UNSAVED
        ) {
            return trimmed
        }
        return RsvpProfileIds.CUSTOM_UNSAVED
    }

    private fun migrateAndReadSelectedProfileId(
        prefs: Preferences,
        customProfiles: List<RsvpCustomProfile>,
    ): String {
        val stored = prefs[keys.rsvpProfile]
        val normalized =
            if (stored == null) {
                RsvpProfileIds.builtIn(RsvpProfile.BALANCED)
            } else {
                normalizeProfileId(stored)
            }
        return when {
            normalized == RsvpProfileIds.CUSTOM_UNSAVED -> normalized
            RsvpProfileIds.isBuiltIn(normalized) -> normalized
            RsvpProfileIds.isCustom(normalized) -> {
                if (customProfiles.any {
                        it.id == normalized
                    }
                ) {
                    normalized
                } else {
                    RsvpProfileIds.CUSTOM_UNSAVED
                }
            }

            else -> RsvpProfileIds.CUSTOM_UNSAVED
        }
    }

    private fun parseCustomProfiles(raw: String?): List<RsvpCustomProfile> {
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
        }.getOrElse { emptyList() }
    }

    private fun encodeCustomProfiles(profiles: List<RsvpCustomProfile>): String {
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
    }

    private fun JSONObject.putPauseScaling(config: RsvpConfig) {
        put("pauseScaleExponent", config.pauseScaleExponent)
        put("minPauseScale", config.minPauseScale)
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
        put("wordsPerFrame", config.wordsPerFrame)
        put("maxChunkLength", config.maxChunkLength)
        put("punctuationPauseFactor", config.punctuationPauseFactor)
        put("longWordMultiplier", config.longWordMultiplier)
        put("useClausePausing", config.useClausePausing)
        put("clausePauseFactor", config.clausePauseFactor)
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
        )

    private fun RsvpConfig.withPauseScalingFromJson(
        obj: JSONObject,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            pauseScaleExponent = obj.optDouble("pauseScaleExponent", defaults.pauseScaleExponent),
            minPauseScale = obj.optDouble("minPauseScale", defaults.minPauseScale),
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
            wordsPerFrame = obj.optInt("wordsPerFrame", defaults.wordsPerFrame),
            maxChunkLength = obj.optInt("maxChunkLength", defaults.maxChunkLength),
            punctuationPauseFactor =
                obj.optDouble("punctuationPauseFactor", defaults.punctuationPauseFactor),
            longWordMultiplier = obj.optDouble("longWordMultiplier", defaults.longWordMultiplier),
            useClausePausing = obj.optBoolean("useClausePausing", defaults.useClausePausing),
            clausePauseFactor =
                normalizeClausePauseFactor(
                    obj.optDouble("clausePauseFactor", defaults.clausePauseFactor),
                    defaults.clausePauseFactor,
                ),
        )

    private fun writeRsvpConfig(
        prefs: MutablePreferences,
        config: RsvpConfig,
    ) {
        val defaults = RsvpConfig()
        writeTiming(prefs, config)
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
        writeBlink(prefs, config)
    }

    private fun writeTiming(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.tempoMsPerWord] = config.tempoMsPerWord.coerceAtLeast(10L)
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
    }

    private fun writePunctuationPauses(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.commaPauseMs] = config.commaPauseMs
        prefs[keys.periodPauseMs] = config.periodPauseMs
        prefs[keys.semicolonPauseMs] = config.semicolonPauseMs
        prefs[keys.colonPauseMs] = config.colonPauseMs
        prefs[keys.dashPauseMs] = config.dashPauseMs
        prefs[keys.parenthesesPauseMs] = config.parenthesesPauseMs
        prefs[keys.quotePauseMs] = config.quotePauseMs
        prefs[keys.sentenceEndPauseMs] = config.sentenceEndPauseMs
        prefs[keys.paragraphPauseMs] = config.paragraphPauseMs
    }

    private fun writePauseScaling(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.pauseScaleExponent] = config.pauseScaleExponent
        prefs[keys.minPauseScale] = config.minPauseScale
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
        prefs[keys.wordsPerFrame] = config.wordsPerFrame
        prefs[keys.maxChunkLength] = config.maxChunkLength
        prefs[keys.punctuationPause] = config.punctuationPauseFactor
        prefs[keys.longWordMultiplier] = config.longWordMultiplier
        prefs[keys.useClausePausing] = config.useClausePausing
        prefs[keys.clausePauseFactor] =
            normalizeClausePauseFactor(config.clausePauseFactor, defaults.clausePauseFactor)
    }

    private fun writeBlink(prefs: MutablePreferences, config: RsvpConfig) {
        prefs[keys.blinkMode] = config.blinkMode.name
        prefs[keys.blinkEnabled] = config.blinkMode != BlinkMode.OFF
    }

    private data class TimingInfo(
        val tempoMsPerWord: Long,
        val baseWpm: Int,
    )

    private fun readTimingInfo(prefs: Preferences, defaults: RsvpConfig): TimingInfo {
        val tempoMsPerWord =
            (
                prefs[keys.tempoMsPerWord]
                    ?: legacyWpmToTempoMs(
                        legacyWpm = prefs[legacyBaseWpmKey],
                        defaultTempoMs = defaults.tempoMsPerWord,
                    )
                ).coerceAtLeast(10L)
        val baseWpm = (60_000.0 / tempoMsPerWord.toDouble()).toInt().coerceAtLeast(1)
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

    private fun readRsvpConfig(prefs: Preferences): RsvpConfig {
        val defaults = RsvpConfig()
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
            .withOrpAndDelays(prefs, defaults)
            .withBlinkMode(blinkMode)
    }

    private fun RsvpConfig.withTiming(timingInfo: TimingInfo): RsvpConfig =
        copy(
            tempoMsPerWord = timingInfo.tempoMsPerWord,
            baseWpm = timingInfo.baseWpm,
        )

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
        )

    private fun RsvpConfig.withPauseScaling(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            pauseScaleExponent =
                prefs.readOrDefault(keys.pauseScaleExponent, defaults.pauseScaleExponent),
            minPauseScale = prefs.readOrDefault(keys.minPauseScale, defaults.minPauseScale),
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
            wordsPerFrame = prefs.readOrDefault(keys.wordsPerFrame, defaults.wordsPerFrame),
            maxChunkLength = prefs.readOrDefault(keys.maxChunkLength, defaults.maxChunkLength),
            punctuationPauseFactor =
                prefs.readOrDefault(keys.punctuationPause, defaults.punctuationPauseFactor),
            longWordMultiplier =
                prefs.readOrDefault(keys.longWordMultiplier, defaults.longWordMultiplier),
            useClausePausing =
                prefs.readOrDefault(keys.useClausePausing, defaults.useClausePausing),
            clausePauseFactor =
                normalizeClausePauseFactor(prefs[keys.clausePauseFactor], defaults.clausePauseFactor),
        )

    private fun RsvpConfig.withOrpAndDelays(
        prefs: Preferences,
        defaults: RsvpConfig,
    ): RsvpConfig =
        copy(
            orpEnabled = prefs.readOrDefault(keys.orpEnabled, defaults.orpEnabled),
            startDelayMs = prefs.readOrDefault(keys.startDelayMs, defaults.startDelayMs),
            endDelayMs = prefs.readOrDefault(keys.endDelayMs, defaults.endDelayMs),
        )

    private fun RsvpConfig.withBlinkMode(blinkMode: BlinkMode): RsvpConfig =
        copy(blinkMode = blinkMode)

    private fun parseBlinkMode(value: String?): BlinkMode? = value?.let {
        runCatching { BlinkMode.valueOf(it) }.getOrNull()
    }
}

private object PrefKeys {
    val tempoMsPerWord = longPreferencesKey("tempo_ms_per_word")
    val rsvpProfile = stringPreferencesKey("rsvp_profile")
    val customRsvpProfilesJson = stringPreferencesKey("custom_rsvp_profiles_json")
    val minWordMs = longPreferencesKey("min_word_ms")
    val longWordMinMs = longPreferencesKey("long_word_min_ms")
    val longWordChars = intPreferencesKey("long_word_chars")
    val syllableExtraMs = longPreferencesKey("syllable_extra_ms")
    val rarityExtraMaxMs = longPreferencesKey("rarity_extra_max_ms")
    val complexityStrength = doublePreferencesKey("complexity_strength")
    val lengthStrength = doublePreferencesKey("length_strength")
    val lengthExponent = doublePreferencesKey("length_exponent")
    val enablePhraseChunking = booleanPreferencesKey("enable_phrase_chunking")
    val maxWordsPerUnit = intPreferencesKey("max_words_per_unit")
    val maxCharsPerUnit = intPreferencesKey("max_chars_per_unit")
    val subwordChunkPauseMs = longPreferencesKey("subword_chunk_pause_ms")
    val commaPauseMs = longPreferencesKey("comma_pause_ms")
    val periodPauseMs = longPreferencesKey("period_pause_ms")
    val semicolonPauseMs = longPreferencesKey("semicolon_pause_ms")
    val colonPauseMs = longPreferencesKey("colon_pause_ms")
    val dashPauseMs = longPreferencesKey("dash_pause_ms")
    val parenthesesPauseMs = longPreferencesKey("parentheses_pause_ms")
    val quotePauseMs = longPreferencesKey("quote_pause_ms")
    val sentenceEndPauseMs = longPreferencesKey("sentence_end_pause_ms")
    val wordsPerFrame = intPreferencesKey("words_per_frame")
    val maxChunkLength = intPreferencesKey("max_chunk_length")
    val punctuationPause = doublePreferencesKey("punctuation_pause_factor")
    val paragraphPauseMs = longPreferencesKey("paragraph_pause_ms")
    val longWordMultiplier = doublePreferencesKey("long_word_multiplier")
    val pauseScaleExponent = doublePreferencesKey("pause_scale_exponent")
    val minPauseScale = doublePreferencesKey("min_pause_scale")
    val parentheticalMultiplier = doublePreferencesKey("parenthetical_multiplier")
    val dialogueMultiplier = doublePreferencesKey("dialogue_multiplier")
    val smoothingAlpha = doublePreferencesKey("rhythm_smoothing_alpha")
    val maxSpeedupFactor = doublePreferencesKey("rhythm_max_speedup_factor")
    val maxSlowdownFactor = doublePreferencesKey("rhythm_max_slowdown_factor")
    val useProsodyPacing = booleanPreferencesKey("use_prosody_pacing")
    val prosodyStrength = doublePreferencesKey("prosody_strength")
    val orpEnabled = booleanPreferencesKey("orp_enabled")
    val startDelayMs = longPreferencesKey("start_delay_ms")
    val endDelayMs = longPreferencesKey("end_delay_ms")
    val rampUpFrames = intPreferencesKey("ramp_up_frames")
    val rampDownFrames = intPreferencesKey("ramp_down_frames")
    val useAdaptiveTiming = booleanPreferencesKey("use_adaptive_timing")
    val adaptiveDifficultyMaxHoldMs = longPreferencesKey("adaptive_difficulty_max_hold_ms")
    val complexWordHoldMs = longPreferencesKey("complex_word_hold_ms")
    val complexWordThreshold = doublePreferencesKey("complex_word_threshold")
    val useClausePausing = booleanPreferencesKey("use_clause_pausing")
    val clausePauseFactor = doublePreferencesKey("clause_pause_factor")
    val blinkMode = stringPreferencesKey("blink_mode")
    val blinkEnabled = booleanPreferencesKey("blink_enabled")
    val readerFontSize = floatPreferencesKey("reader_font_size")
    val readerTheme = stringPreferencesKey("reader_theme")
    val readerTextBrightness = floatPreferencesKey("reader_text_brightness")
    val invertedScroll = booleanPreferencesKey("inverted_scroll")
    val rsvpFontSize = floatPreferencesKey("rsvp_font_size")
    val rsvpTextBrightness = floatPreferencesKey("rsvp_text_brightness")
    val rsvpFontWeight = stringPreferencesKey("rsvp_font_weight")
    val rsvpFontFamily = stringPreferencesKey("rsvp_font_family")
    val rsvpVerticalBias = floatPreferencesKey("rsvp_vertical_bias")
    val rsvpHorizontalBias = floatPreferencesKey("rsvp_horizontal_bias")
    val unlockExtremeSpeed = booleanPreferencesKey("unlock_extreme_speed")
    val focusModeEnabled = booleanPreferencesKey("focus_mode_enabled")
    val focusHideStatusBar = booleanPreferencesKey("focus_hide_status_bar")
    val focusPauseNotifications = booleanPreferencesKey("focus_pause_notifications")
    val focusApplyInReader = booleanPreferencesKey("focus_apply_in_reader")
    val focusApplyInRsvp = booleanPreferencesKey("focus_apply_in_rsvp")
}
