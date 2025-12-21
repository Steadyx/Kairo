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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class PreferencesRepositoryImpl(private val context: Context,) : PreferencesRepository {
    private val keys = PrefKeys

    override val preferences: Flow<UserPreferences> =
        context.dataStore.data
            .onEach { prefs ->
                if (prefs.contains(legacyBaseWpmKey)) {
                    context.dataStore.edit { mutable ->
                        if (!mutable.contains(keys.tempoMsPerWord)) {
                            val legacyWpm = mutable[legacyBaseWpmKey]
                            val tempoMs =
                                when {
                                    legacyWpm == null -> RsvpConfig().tempoMsPerWord
                                    legacyWpm <= 0 -> RsvpConfig().tempoMsPerWord
                                    else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(
                                        10L
                                    )
                                }
                            mutable[keys.tempoMsPerWord] = tempoMs
                        }
                        mutable.remove(legacyBaseWpmKey)
                    }
                }
            }.map { prefs ->
                val defaults = UserPreferences()
                val customProfiles = parseCustomProfiles(prefs[keys.customRsvpProfilesJson])
                val selectedProfileId = migrateAndReadSelectedProfileId(prefs, customProfiles)
                val derivedTempoMs =
                    (
                        prefs[keys.tempoMsPerWord] ?: run {
                            val legacyWpm = prefs[legacyBaseWpmKey]
                            when {
                                legacyWpm == null -> RsvpConfig().tempoMsPerWord
                                legacyWpm <= 0 -> RsvpConfig().tempoMsPerWord
                                else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(
                                    10L
                                )
                            }
                        }
                        ).coerceAtLeast(10L)
                val derivedBaseWpm = (60_000.0 / derivedTempoMs.toDouble()).toInt().coerceAtLeast(1)

                val storedBlinkMode = parseBlinkMode(prefs[keys.blinkMode])
                val blinkMode =
                    storedBlinkMode
                        ?: if (prefs[keys.blinkEnabled] ==
                            true
                        ) {
                            BlinkMode.SUBTLE
                        } else {
                            RsvpConfig().blinkMode
                        }

                UserPreferences(
                    rsvpConfig =
                    RsvpConfig(
                        tempoMsPerWord = derivedTempoMs,
                        baseWpm = derivedBaseWpm,
                        minWordMs = prefs[keys.minWordMs] ?: RsvpConfig().minWordMs,
                        longWordMinMs = prefs[keys.longWordMinMs] ?: RsvpConfig().longWordMinMs,
                        longWordChars = prefs[keys.longWordChars] ?: RsvpConfig().longWordChars,
                        syllableExtraMs =
                        prefs[keys.syllableExtraMs] ?: RsvpConfig().syllableExtraMs,
                        rarityExtraMaxMs =
                        prefs[keys.rarityExtraMaxMs]
                            ?: RsvpConfig().rarityExtraMaxMs,
                        complexityStrength =
                        prefs[keys.complexityStrength]
                            ?: RsvpConfig().complexityStrength,
                        lengthStrength =
                        prefs[keys.lengthStrength] ?: RsvpConfig().lengthStrength,
                        lengthExponent =
                        prefs[keys.lengthExponent] ?: RsvpConfig().lengthExponent,
                        enablePhraseChunking =
                        prefs[keys.enablePhraseChunking]
                            ?: RsvpConfig().enablePhraseChunking,
                        maxWordsPerUnit =
                        prefs[keys.maxWordsPerUnit] ?: RsvpConfig().maxWordsPerUnit,
                        maxCharsPerUnit =
                        prefs[keys.maxCharsPerUnit] ?: RsvpConfig().maxCharsPerUnit,
                        subwordChunkPauseMs =
                        prefs[keys.subwordChunkPauseMs]
                            ?: RsvpConfig().subwordChunkPauseMs,
                        commaPauseMs = prefs[keys.commaPauseMs] ?: RsvpConfig().commaPauseMs,
                        semicolonPauseMs =
                        prefs[keys.semicolonPauseMs]
                            ?: RsvpConfig().semicolonPauseMs,
                        colonPauseMs = prefs[keys.colonPauseMs] ?: RsvpConfig().colonPauseMs,
                        dashPauseMs = prefs[keys.dashPauseMs] ?: RsvpConfig().dashPauseMs,
                        parenthesesPauseMs =
                        prefs[keys.parenthesesPauseMs]
                            ?: RsvpConfig().parenthesesPauseMs,
                        quotePauseMs = prefs[keys.quotePauseMs] ?: RsvpConfig().quotePauseMs,
                        sentenceEndPauseMs =
                        prefs[keys.sentenceEndPauseMs]
                            ?: RsvpConfig().sentenceEndPauseMs,
                        paragraphPauseMs =
                        prefs[keys.paragraphPauseMs]
                            ?: RsvpConfig().paragraphPauseMs,
                        pauseScaleExponent =
                        prefs[keys.pauseScaleExponent]
                            ?: RsvpConfig().pauseScaleExponent,
                        minPauseScale = prefs[keys.minPauseScale] ?: RsvpConfig().minPauseScale,
                        parentheticalMultiplier =
                        prefs[keys.parentheticalMultiplier]
                            ?: RsvpConfig().parentheticalMultiplier,
                        dialogueMultiplier =
                        prefs[keys.dialogueMultiplier]
                            ?: RsvpConfig().dialogueMultiplier,
                        smoothingAlpha =
                        prefs[keys.smoothingAlpha] ?: RsvpConfig().smoothingAlpha,
                        maxSpeedupFactor =
                        prefs[keys.maxSpeedupFactor]
                            ?: RsvpConfig().maxSpeedupFactor,
                        maxSlowdownFactor =
                        prefs[keys.maxSlowdownFactor]
                            ?: RsvpConfig().maxSlowdownFactor,
                        orpEnabled = prefs[keys.orpEnabled] ?: RsvpConfig().orpEnabled,
                        startDelayMs = prefs[keys.startDelayMs] ?: RsvpConfig().startDelayMs,
                        endDelayMs = prefs[keys.endDelayMs] ?: RsvpConfig().endDelayMs,
                        rampUpFrames = prefs[keys.rampUpFrames] ?: RsvpConfig().rampUpFrames,
                        rampDownFrames =
                        prefs[keys.rampDownFrames] ?: RsvpConfig().rampDownFrames,
                        // Legacy persisted fields
                        wordsPerFrame = prefs[keys.wordsPerFrame] ?: RsvpConfig().wordsPerFrame,
                        maxChunkLength =
                        prefs[keys.maxChunkLength] ?: RsvpConfig().maxChunkLength,
                        punctuationPauseFactor =
                        prefs[keys.punctuationPause]
                            ?: RsvpConfig().punctuationPauseFactor,
                        longWordMultiplier =
                        prefs[keys.longWordMultiplier]
                            ?: RsvpConfig().longWordMultiplier,
                        useClausePausing =
                        prefs[keys.useClausePausing]
                            ?: RsvpConfig().useClausePausing,
                        clausePauseFactor =
                        (
                            prefs[keys.clausePauseFactor]?.takeIf { it.isFinite() }
                                ?: RsvpConfig().clausePauseFactor
                            ).coerceIn(1.0, 1.6),
                        blinkMode = blinkMode,
                    ),
                    rsvpSelectedProfileId = selectedProfileId,
                    rsvpCustomProfiles = customProfiles,
                    readerFontSizeSp = prefs[keys.readerFontSize] ?: defaults.readerFontSizeSp,
                    readerTheme =
                    prefs[keys.readerTheme]?.let { value ->
                        runCatching { ReaderTheme.valueOf(value) }.getOrNull()
                    } ?: defaults.readerTheme,
                    readerTextBrightness =
                    (
                        prefs[keys.readerTextBrightness]
                            ?: defaults.readerTextBrightness
                        ).coerceIn(0.55f, 1.0f),
                    invertedScroll = prefs[keys.invertedScroll] ?: defaults.invertedScroll,
                    rsvpFontSizeSp = prefs[keys.rsvpFontSize] ?: defaults.rsvpFontSizeSp,
                    rsvpTextBrightness =
                    (
                        prefs[keys.rsvpTextBrightness]
                            ?: defaults.rsvpTextBrightness
                        ).coerceIn(0.55f, 1.0f),
                    rsvpFontWeight =
                    prefs[keys.rsvpFontWeight]?.let { value ->
                        runCatching { RsvpFontWeight.valueOf(value) }.getOrNull()
                    } ?: defaults.rsvpFontWeight,
                    rsvpFontFamily =
                    prefs[keys.rsvpFontFamily]?.let { value ->
                        runCatching { RsvpFontFamily.valueOf(value) }.getOrNull()
                    } ?: defaults.rsvpFontFamily,
                    rsvpVerticalBias = prefs[keys.rsvpVerticalBias] ?: defaults.rsvpVerticalBias,
                    rsvpHorizontalBias =
                    prefs[keys.rsvpHorizontalBias] ?: defaults.rsvpHorizontalBias,
                    unlockExtremeSpeed = prefs[keys.unlockExtremeSpeed] ?: (derivedTempoMs < 30L),
                    focusModeEnabled = prefs[keys.focusModeEnabled] ?: defaults.focusModeEnabled,
                    focusHideStatusBar =
                    prefs[keys.focusHideStatusBar] ?: defaults.focusHideStatusBar,
                    focusPauseNotifications =
                    prefs[keys.focusPauseNotifications]
                        ?: defaults.focusPauseNotifications,
                    focusApplyInReader =
                    prefs[keys.focusApplyInReader] ?: defaults.focusApplyInReader,
                    focusApplyInRsvp = prefs[keys.focusApplyInRsvp] ?: defaults.focusApplyInRsvp,
                )
            }

    override suspend fun updateRsvpConfig(updater: (RsvpConfig) -> RsvpConfig) {
        context.dataStore.edit { prefs ->
            val updated = updater(readConfig(prefs))
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
            prefs[keys.readerTextBrightness] = brightness.coerceIn(0.55f, 1.0f)
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
            prefs[keys.rsvpTextBrightness] = brightness.coerceIn(0.55f, 1.0f)
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

    private fun normalizeProfileId(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return RsvpProfileIds.CUSTOM_UNSAVED
        if (trimmed == "CUSTOM") return RsvpProfileIds.CUSTOM_UNSAVED
        if (trimmed == "BALANCED" ||
            trimmed == "CHILL" ||
            trimmed == "SPRINT" ||
            trimmed == "STUDY"
        ) {
            val parsed =
                runCatching { RsvpProfile.valueOf(trimmed) }.getOrNull() ?: RsvpProfile.BALANCED
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

    private fun encodeRsvpConfig(config: RsvpConfig): JSONObject {
        val o = JSONObject()
        o.put("tempoMsPerWord", config.tempoMsPerWord)
        o.put("minWordMs", config.minWordMs)
        o.put("longWordMinMs", config.longWordMinMs)
        o.put("longWordChars", config.longWordChars)
        o.put("syllableExtraMs", config.syllableExtraMs)
        o.put("rarityExtraMaxMs", config.rarityExtraMaxMs)
        o.put("complexityStrength", config.complexityStrength)
        o.put("lengthStrength", config.lengthStrength)
        o.put("lengthExponent", config.lengthExponent)
        o.put("enablePhraseChunking", config.enablePhraseChunking)
        o.put("maxWordsPerUnit", config.maxWordsPerUnit)
        o.put("maxCharsPerUnit", config.maxCharsPerUnit)
        o.put("subwordChunkPauseMs", config.subwordChunkPauseMs)
        o.put("commaPauseMs", config.commaPauseMs)
        o.put("semicolonPauseMs", config.semicolonPauseMs)
        o.put("colonPauseMs", config.colonPauseMs)
        o.put("dashPauseMs", config.dashPauseMs)
        o.put("parenthesesPauseMs", config.parenthesesPauseMs)
        o.put("quotePauseMs", config.quotePauseMs)
        o.put("sentenceEndPauseMs", config.sentenceEndPauseMs)
        o.put("paragraphPauseMs", config.paragraphPauseMs)
        o.put("pauseScaleExponent", config.pauseScaleExponent)
        o.put("minPauseScale", config.minPauseScale)
        o.put("parentheticalMultiplier", config.parentheticalMultiplier)
        o.put("dialogueMultiplier", config.dialogueMultiplier)
        o.put("smoothingAlpha", config.smoothingAlpha)
        o.put("maxSpeedupFactor", config.maxSpeedupFactor)
        o.put("maxSlowdownFactor", config.maxSlowdownFactor)
        o.put("orpEnabled", config.orpEnabled)
        o.put("startDelayMs", config.startDelayMs)
        o.put("endDelayMs", config.endDelayMs)
        o.put("rampUpFrames", config.rampUpFrames)
        o.put("rampDownFrames", config.rampDownFrames)
        o.put("wordsPerFrame", config.wordsPerFrame)
        o.put("maxChunkLength", config.maxChunkLength)
        o.put("punctuationPauseFactor", config.punctuationPauseFactor)
        o.put("longWordMultiplier", config.longWordMultiplier)
        o.put("useClausePausing", config.useClausePausing)
        o.put("clausePauseFactor", config.clausePauseFactor)
        o.put("blinkMode", config.blinkMode.name)
        o.put("blinkEnabled", config.blinkMode != BlinkMode.OFF)
        return o
    }

    private fun decodeRsvpConfig(obj: JSONObject): RsvpConfig {
        val d = RsvpConfig()
        val clausePauseFactor =
            obj
                .optDouble("clausePauseFactor", d.clausePauseFactor)
                .takeIf { it.isFinite() }
                ?.coerceIn(1.0, 1.6)
                ?: d.clausePauseFactor
        val blinkModeRaw = obj.optString("blinkMode", "")
        val blinkMode =
            parseBlinkMode(blinkModeRaw.takeIf { it.isNotBlank() })
                ?: if (obj.optBoolean("blinkEnabled", false)) BlinkMode.SUBTLE else d.blinkMode
        return RsvpConfig(
            tempoMsPerWord = obj.optLong("tempoMsPerWord", d.tempoMsPerWord),
            minWordMs = obj.optLong("minWordMs", d.minWordMs),
            longWordMinMs = obj.optLong("longWordMinMs", d.longWordMinMs),
            longWordChars = obj.optInt("longWordChars", d.longWordChars),
            syllableExtraMs = obj.optLong("syllableExtraMs", d.syllableExtraMs),
            rarityExtraMaxMs = obj.optLong("rarityExtraMaxMs", d.rarityExtraMaxMs),
            complexityStrength = obj.optDouble("complexityStrength", d.complexityStrength),
            lengthStrength = obj.optDouble("lengthStrength", d.lengthStrength),
            lengthExponent = obj.optDouble("lengthExponent", d.lengthExponent),
            enablePhraseChunking = obj.optBoolean("enablePhraseChunking", d.enablePhraseChunking),
            maxWordsPerUnit = obj.optInt("maxWordsPerUnit", d.maxWordsPerUnit),
            maxCharsPerUnit = obj.optInt("maxCharsPerUnit", d.maxCharsPerUnit),
            subwordChunkPauseMs = obj.optLong("subwordChunkPauseMs", d.subwordChunkPauseMs),
            commaPauseMs = obj.optLong("commaPauseMs", d.commaPauseMs),
            semicolonPauseMs = obj.optLong("semicolonPauseMs", d.semicolonPauseMs),
            colonPauseMs = obj.optLong("colonPauseMs", d.colonPauseMs),
            dashPauseMs = obj.optLong("dashPauseMs", d.dashPauseMs),
            parenthesesPauseMs = obj.optLong("parenthesesPauseMs", d.parenthesesPauseMs),
            quotePauseMs = obj.optLong("quotePauseMs", d.quotePauseMs),
            sentenceEndPauseMs = obj.optLong("sentenceEndPauseMs", d.sentenceEndPauseMs),
            paragraphPauseMs = obj.optLong("paragraphPauseMs", d.paragraphPauseMs),
            pauseScaleExponent = obj.optDouble("pauseScaleExponent", d.pauseScaleExponent),
            minPauseScale = obj.optDouble("minPauseScale", d.minPauseScale),
            parentheticalMultiplier =
            obj.optDouble(
                "parentheticalMultiplier",
                d.parentheticalMultiplier,
            ),
            dialogueMultiplier = obj.optDouble("dialogueMultiplier", d.dialogueMultiplier),
            smoothingAlpha = obj.optDouble("smoothingAlpha", d.smoothingAlpha),
            maxSpeedupFactor = obj.optDouble("maxSpeedupFactor", d.maxSpeedupFactor),
            maxSlowdownFactor = obj.optDouble("maxSlowdownFactor", d.maxSlowdownFactor),
            orpEnabled = obj.optBoolean("orpEnabled", d.orpEnabled),
            startDelayMs = obj.optLong("startDelayMs", d.startDelayMs),
            endDelayMs = obj.optLong("endDelayMs", d.endDelayMs),
            rampUpFrames = obj.optInt("rampUpFrames", d.rampUpFrames),
            rampDownFrames = obj.optInt("rampDownFrames", d.rampDownFrames),
            wordsPerFrame = obj.optInt("wordsPerFrame", d.wordsPerFrame),
            maxChunkLength = obj.optInt("maxChunkLength", d.maxChunkLength),
            punctuationPauseFactor =
            obj.optDouble(
                "punctuationPauseFactor",
                d.punctuationPauseFactor,
            ),
            longWordMultiplier = obj.optDouble("longWordMultiplier", d.longWordMultiplier),
            baseWpm = d.baseWpm,
            useAdaptiveTiming = d.useAdaptiveTiming,
            useClausePausing = obj.optBoolean("useClausePausing", d.useClausePausing),
            useDialogueDetection = d.useDialogueDetection,
            complexWordThreshold = d.complexWordThreshold,
            clausePauseFactor = clausePauseFactor,
            blinkMode = blinkMode,
        )
    }

    private fun writeRsvpConfig(
        prefs: MutablePreferences,
        config: RsvpConfig,
    ) {
        prefs[keys.tempoMsPerWord] = config.tempoMsPerWord.coerceAtLeast(10L)
        prefs.remove(legacyBaseWpmKey)
        prefs[keys.minWordMs] = config.minWordMs
        prefs[keys.longWordMinMs] = config.longWordMinMs
        prefs[keys.longWordChars] = config.longWordChars
        prefs[keys.syllableExtraMs] = config.syllableExtraMs
        prefs[keys.rarityExtraMaxMs] = config.rarityExtraMaxMs
        prefs[keys.complexityStrength] = config.complexityStrength
        prefs[keys.lengthStrength] = config.lengthStrength
        prefs[keys.lengthExponent] = config.lengthExponent
        prefs[keys.enablePhraseChunking] = config.enablePhraseChunking
        prefs[keys.maxWordsPerUnit] = config.maxWordsPerUnit
        prefs[keys.maxCharsPerUnit] = config.maxCharsPerUnit
        prefs[keys.subwordChunkPauseMs] = config.subwordChunkPauseMs
        prefs[keys.commaPauseMs] = config.commaPauseMs
        prefs[keys.semicolonPauseMs] = config.semicolonPauseMs
        prefs[keys.colonPauseMs] = config.colonPauseMs
        prefs[keys.dashPauseMs] = config.dashPauseMs
        prefs[keys.parenthesesPauseMs] = config.parenthesesPauseMs
        prefs[keys.quotePauseMs] = config.quotePauseMs
        prefs[keys.sentenceEndPauseMs] = config.sentenceEndPauseMs
        prefs[keys.paragraphPauseMs] = config.paragraphPauseMs
        prefs[keys.pauseScaleExponent] = config.pauseScaleExponent
        prefs[keys.minPauseScale] = config.minPauseScale
        prefs[keys.parentheticalMultiplier] = config.parentheticalMultiplier
        prefs[keys.dialogueMultiplier] = config.dialogueMultiplier
        prefs[keys.smoothingAlpha] = config.smoothingAlpha
        prefs[keys.maxSpeedupFactor] = config.maxSpeedupFactor
        prefs[keys.maxSlowdownFactor] = config.maxSlowdownFactor
        prefs[keys.orpEnabled] = config.orpEnabled
        prefs[keys.startDelayMs] = config.startDelayMs
        prefs[keys.endDelayMs] = config.endDelayMs
        prefs[keys.rampUpFrames] = config.rampUpFrames
        prefs[keys.rampDownFrames] = config.rampDownFrames
        // Legacy persisted fields
        prefs[keys.wordsPerFrame] = config.wordsPerFrame
        prefs[keys.maxChunkLength] = config.maxChunkLength
        prefs[keys.punctuationPause] = config.punctuationPauseFactor
        prefs[keys.longWordMultiplier] = config.longWordMultiplier
        prefs[keys.useClausePausing] = config.useClausePausing
        prefs[keys.clausePauseFactor] = config.clausePauseFactor
            .takeIf { it.isFinite() }
            ?.coerceIn(1.0, 1.6)
            ?: RsvpConfig().clausePauseFactor
        prefs[keys.blinkMode] = config.blinkMode.name
        prefs[keys.blinkEnabled] = config.blinkMode != BlinkMode.OFF
    }

    private fun readConfig(prefs: Preferences): RsvpConfig {
        val tempoMsPerWord =
            (
                prefs[keys.tempoMsPerWord] ?: run {
                    val legacyWpm = prefs[legacyBaseWpmKey]
                    when {
                        legacyWpm == null -> RsvpConfig().tempoMsPerWord
                        legacyWpm <= 0 -> RsvpConfig().tempoMsPerWord
                        else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(10L)
                    }
                }
                ).coerceAtLeast(10L)
        val derivedBaseWpm = (60_000.0 / tempoMsPerWord.toDouble()).toInt().coerceAtLeast(1)

        val storedBlinkMode = parseBlinkMode(prefs[keys.blinkMode])
        val blinkMode =
            storedBlinkMode
                ?: if (prefs[keys.blinkEnabled] ==
                    true
                ) {
                    BlinkMode.SUBTLE
                } else {
                    RsvpConfig().blinkMode
                }

        return RsvpConfig(
            tempoMsPerWord = tempoMsPerWord,
            baseWpm = derivedBaseWpm,
            minWordMs = prefs[keys.minWordMs] ?: RsvpConfig().minWordMs,
            longWordMinMs = prefs[keys.longWordMinMs] ?: RsvpConfig().longWordMinMs,
            longWordChars = prefs[keys.longWordChars] ?: RsvpConfig().longWordChars,
            syllableExtraMs = prefs[keys.syllableExtraMs] ?: RsvpConfig().syllableExtraMs,
            rarityExtraMaxMs = prefs[keys.rarityExtraMaxMs] ?: RsvpConfig().rarityExtraMaxMs,
            complexityStrength = prefs[keys.complexityStrength] ?: RsvpConfig().complexityStrength,
            lengthStrength = prefs[keys.lengthStrength] ?: RsvpConfig().lengthStrength,
            lengthExponent = prefs[keys.lengthExponent] ?: RsvpConfig().lengthExponent,
            enablePhraseChunking =
            prefs[keys.enablePhraseChunking]
                ?: RsvpConfig().enablePhraseChunking,
            maxWordsPerUnit = prefs[keys.maxWordsPerUnit] ?: RsvpConfig().maxWordsPerUnit,
            maxCharsPerUnit = prefs[keys.maxCharsPerUnit] ?: RsvpConfig().maxCharsPerUnit,
            subwordChunkPauseMs =
            prefs[keys.subwordChunkPauseMs]
                ?: RsvpConfig().subwordChunkPauseMs,
            commaPauseMs = prefs[keys.commaPauseMs] ?: RsvpConfig().commaPauseMs,
            semicolonPauseMs = prefs[keys.semicolonPauseMs] ?: RsvpConfig().semicolonPauseMs,
            colonPauseMs = prefs[keys.colonPauseMs] ?: RsvpConfig().colonPauseMs,
            dashPauseMs = prefs[keys.dashPauseMs] ?: RsvpConfig().dashPauseMs,
            parenthesesPauseMs = prefs[keys.parenthesesPauseMs] ?: RsvpConfig().parenthesesPauseMs,
            quotePauseMs = prefs[keys.quotePauseMs] ?: RsvpConfig().quotePauseMs,
            sentenceEndPauseMs = prefs[keys.sentenceEndPauseMs] ?: RsvpConfig().sentenceEndPauseMs,
            paragraphPauseMs = prefs[keys.paragraphPauseMs] ?: RsvpConfig().paragraphPauseMs,
            pauseScaleExponent = prefs[keys.pauseScaleExponent] ?: RsvpConfig().pauseScaleExponent,
            minPauseScale = prefs[keys.minPauseScale] ?: RsvpConfig().minPauseScale,
            parentheticalMultiplier =
            prefs[keys.parentheticalMultiplier]
                ?: RsvpConfig().parentheticalMultiplier,
            dialogueMultiplier = prefs[keys.dialogueMultiplier] ?: RsvpConfig().dialogueMultiplier,
            smoothingAlpha = prefs[keys.smoothingAlpha] ?: RsvpConfig().smoothingAlpha,
            maxSpeedupFactor = prefs[keys.maxSpeedupFactor] ?: RsvpConfig().maxSpeedupFactor,
            maxSlowdownFactor = prefs[keys.maxSlowdownFactor] ?: RsvpConfig().maxSlowdownFactor,
            rampUpFrames = prefs[keys.rampUpFrames] ?: RsvpConfig().rampUpFrames,
            rampDownFrames = prefs[keys.rampDownFrames] ?: RsvpConfig().rampDownFrames,
            wordsPerFrame = prefs[keys.wordsPerFrame] ?: RsvpConfig().wordsPerFrame,
            maxChunkLength = prefs[keys.maxChunkLength] ?: RsvpConfig().maxChunkLength,
            punctuationPauseFactor =
            prefs[keys.punctuationPause]
                ?: RsvpConfig().punctuationPauseFactor,
            longWordMultiplier = prefs[keys.longWordMultiplier] ?: RsvpConfig().longWordMultiplier,
            useClausePausing = prefs[keys.useClausePausing] ?: RsvpConfig().useClausePausing,
            clausePauseFactor =
            (
                prefs[keys.clausePauseFactor]?.takeIf { it.isFinite() }
                    ?: RsvpConfig().clausePauseFactor
                ).coerceIn(1.0, 1.6),
            blinkMode = blinkMode,
            orpEnabled = prefs[keys.orpEnabled] ?: RsvpConfig().orpEnabled,
            startDelayMs = prefs[keys.startDelayMs] ?: RsvpConfig().startDelayMs,
            endDelayMs = prefs[keys.endDelayMs] ?: RsvpConfig().endDelayMs,
        )
    }

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
    val orpEnabled = booleanPreferencesKey("orp_enabled")
    val startDelayMs = longPreferencesKey("start_delay_ms")
    val endDelayMs = longPreferencesKey("end_delay_ms")
    val rampUpFrames = intPreferencesKey("ramp_up_frames")
    val rampDownFrames = intPreferencesKey("ramp_down_frames")
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
