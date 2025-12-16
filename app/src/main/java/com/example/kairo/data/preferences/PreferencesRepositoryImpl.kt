package com.example.kairo.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map

private val legacyBaseWpmKey = intPreferencesKey("base_wpm")

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class PreferencesRepositoryImpl(
    private val context: Context
) : PreferencesRepository {

    private val keys = PrefKeys

    override val preferences: Flow<UserPreferences> = context.dataStore.data
        .onEach { prefs ->
            if (prefs.contains(legacyBaseWpmKey)) {
                context.dataStore.edit { mutable ->
                    if (!mutable.contains(keys.tempoMsPerWord)) {
                        val legacyWpm = mutable[legacyBaseWpmKey]
                        val tempoMs = when {
                            legacyWpm == null -> RsvpConfig().tempoMsPerWord
                            legacyWpm <= 0 -> RsvpConfig().tempoMsPerWord
                            else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(10L)
                        }
                        mutable[keys.tempoMsPerWord] = tempoMs
                    }
                    mutable.remove(legacyBaseWpmKey)
                }
            }
        }
        .map { prefs ->
        val derivedTempoMs = (prefs[keys.tempoMsPerWord] ?: run {
            val legacyWpm = prefs[legacyBaseWpmKey]
            when {
                legacyWpm == null -> RsvpConfig().tempoMsPerWord
                legacyWpm <= 0 -> RsvpConfig().tempoMsPerWord
                else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(10L)
            }
        }).coerceAtLeast(10L)
        val derivedBaseWpm = (60_000.0 / derivedTempoMs.toDouble()).toInt().coerceAtLeast(1)

        UserPreferences(
            rsvpConfig = RsvpConfig(
                tempoMsPerWord = derivedTempoMs,
                baseWpm = derivedBaseWpm,
                minWordMs = prefs[keys.minWordMs] ?: RsvpConfig().minWordMs,
                longWordMinMs = prefs[keys.longWordMinMs] ?: RsvpConfig().longWordMinMs,
                longWordChars = prefs[keys.longWordChars] ?: RsvpConfig().longWordChars,
                syllableExtraMs = prefs[keys.syllableExtraMs] ?: RsvpConfig().syllableExtraMs,
                rarityExtraMaxMs = prefs[keys.rarityExtraMaxMs] ?: RsvpConfig().rarityExtraMaxMs,
                complexityStrength = prefs[keys.complexityStrength] ?: RsvpConfig().complexityStrength,
                lengthStrength = prefs[keys.lengthStrength] ?: RsvpConfig().lengthStrength,
                lengthExponent = prefs[keys.lengthExponent] ?: RsvpConfig().lengthExponent,
                enablePhraseChunking = prefs[keys.enablePhraseChunking] ?: RsvpConfig().enablePhraseChunking,
                maxWordsPerUnit = prefs[keys.maxWordsPerUnit] ?: RsvpConfig().maxWordsPerUnit,
                maxCharsPerUnit = prefs[keys.maxCharsPerUnit] ?: RsvpConfig().maxCharsPerUnit,
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
                parentheticalMultiplier = prefs[keys.parentheticalMultiplier] ?: RsvpConfig().parentheticalMultiplier,
                dialogueMultiplier = prefs[keys.dialogueMultiplier] ?: RsvpConfig().dialogueMultiplier,
                smoothingAlpha = prefs[keys.smoothingAlpha] ?: RsvpConfig().smoothingAlpha,
                maxSpeedupFactor = prefs[keys.maxSpeedupFactor] ?: RsvpConfig().maxSpeedupFactor,
                maxSlowdownFactor = prefs[keys.maxSlowdownFactor] ?: RsvpConfig().maxSlowdownFactor,
                orpEnabled = prefs[keys.orpEnabled] ?: RsvpConfig().orpEnabled,
                startDelayMs = prefs[keys.startDelayMs] ?: RsvpConfig().startDelayMs,
                endDelayMs = prefs[keys.endDelayMs] ?: RsvpConfig().endDelayMs,
                rampUpFrames = prefs[keys.rampUpFrames] ?: RsvpConfig().rampUpFrames,
                rampDownFrames = prefs[keys.rampDownFrames] ?: RsvpConfig().rampDownFrames,
                // Legacy persisted fields
                wordsPerFrame = prefs[keys.wordsPerFrame] ?: RsvpConfig().wordsPerFrame,
                maxChunkLength = prefs[keys.maxChunkLength] ?: RsvpConfig().maxChunkLength,
                punctuationPauseFactor = prefs[keys.punctuationPause] ?: RsvpConfig().punctuationPauseFactor,
                longWordMultiplier = prefs[keys.longWordMultiplier] ?: RsvpConfig().longWordMultiplier
            ),
            readerFontSizeSp = prefs[keys.readerFontSize] ?: UserPreferences().readerFontSizeSp,
            readerTheme = prefs[keys.readerTheme]?.let { value ->
                runCatching { ReaderTheme.valueOf(value) }.getOrNull()
            } ?: UserPreferences().readerTheme,
            invertedScroll = prefs[keys.invertedScroll] ?: UserPreferences().invertedScroll,
            rsvpFontSizeSp = prefs[keys.rsvpFontSize] ?: UserPreferences().rsvpFontSizeSp,
            rsvpFontWeight = prefs[keys.rsvpFontWeight]?.let { value ->
                runCatching { RsvpFontWeight.valueOf(value) }.getOrNull()
            } ?: UserPreferences().rsvpFontWeight,
            rsvpFontFamily = prefs[keys.rsvpFontFamily]?.let { value ->
                runCatching { RsvpFontFamily.valueOf(value) }.getOrNull()
            } ?: UserPreferences().rsvpFontFamily,
            rsvpVerticalBias = prefs[keys.rsvpVerticalBias] ?: UserPreferences().rsvpVerticalBias,
            rsvpHorizontalBias = prefs[keys.rsvpHorizontalBias] ?: UserPreferences().rsvpHorizontalBias,
            unlockExtremeSpeed = prefs[keys.unlockExtremeSpeed] ?: (derivedTempoMs < 30L),
            focusModeEnabled = prefs[keys.focusModeEnabled] ?: UserPreferences().focusModeEnabled,
            focusHideStatusBar = prefs[keys.focusHideStatusBar] ?: UserPreferences().focusHideStatusBar,
            focusPauseNotifications = prefs[keys.focusPauseNotifications] ?: UserPreferences().focusPauseNotifications,
            focusApplyInReader = prefs[keys.focusApplyInReader] ?: UserPreferences().focusApplyInReader,
            focusApplyInRsvp = prefs[keys.focusApplyInRsvp] ?: UserPreferences().focusApplyInRsvp
        )
    }

    override suspend fun updateRsvpConfig(updater: (RsvpConfig) -> RsvpConfig) {
        context.dataStore.edit { prefs ->
            val updated = updater(readConfig(prefs))
            prefs[keys.tempoMsPerWord] = updated.tempoMsPerWord
            prefs.remove(legacyBaseWpmKey)
            prefs[keys.minWordMs] = updated.minWordMs
            prefs[keys.longWordMinMs] = updated.longWordMinMs
            prefs[keys.longWordChars] = updated.longWordChars
            prefs[keys.syllableExtraMs] = updated.syllableExtraMs
            prefs[keys.rarityExtraMaxMs] = updated.rarityExtraMaxMs
            prefs[keys.complexityStrength] = updated.complexityStrength
            prefs[keys.lengthStrength] = updated.lengthStrength
            prefs[keys.lengthExponent] = updated.lengthExponent
            prefs[keys.enablePhraseChunking] = updated.enablePhraseChunking
            prefs[keys.maxWordsPerUnit] = updated.maxWordsPerUnit
            prefs[keys.maxCharsPerUnit] = updated.maxCharsPerUnit
            prefs[keys.commaPauseMs] = updated.commaPauseMs
            prefs[keys.semicolonPauseMs] = updated.semicolonPauseMs
            prefs[keys.colonPauseMs] = updated.colonPauseMs
            prefs[keys.dashPauseMs] = updated.dashPauseMs
            prefs[keys.parenthesesPauseMs] = updated.parenthesesPauseMs
            prefs[keys.quotePauseMs] = updated.quotePauseMs
            prefs[keys.sentenceEndPauseMs] = updated.sentenceEndPauseMs
            prefs[keys.wordsPerFrame] = updated.wordsPerFrame
            prefs[keys.maxChunkLength] = updated.maxChunkLength
            prefs[keys.punctuationPause] = updated.punctuationPauseFactor
            prefs[keys.paragraphPauseMs] = updated.paragraphPauseMs
            prefs[keys.longWordMultiplier] = updated.longWordMultiplier
            prefs[keys.pauseScaleExponent] = updated.pauseScaleExponent
            prefs[keys.minPauseScale] = updated.minPauseScale
            prefs[keys.parentheticalMultiplier] = updated.parentheticalMultiplier
            prefs[keys.dialogueMultiplier] = updated.dialogueMultiplier
            prefs[keys.smoothingAlpha] = updated.smoothingAlpha
            prefs[keys.maxSpeedupFactor] = updated.maxSpeedupFactor
            prefs[keys.maxSlowdownFactor] = updated.maxSlowdownFactor
            prefs[keys.orpEnabled] = updated.orpEnabled
            prefs[keys.startDelayMs] = updated.startDelayMs
            prefs[keys.endDelayMs] = updated.endDelayMs
            prefs[keys.rampUpFrames] = updated.rampUpFrames
            prefs[keys.rampDownFrames] = updated.rampDownFrames
        }
    }

    override suspend fun updateUnlockExtremeSpeed(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.unlockExtremeSpeed] = enabled }
    }

    override suspend fun updateFontSize(size: Float) {
        context.dataStore.edit { prefs -> prefs[keys.readerFontSize] = size }
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

    private fun readConfig(prefs: Preferences): RsvpConfig {
        val tempoMsPerWord = (prefs[keys.tempoMsPerWord] ?: run {
            val legacyWpm = prefs[legacyBaseWpmKey]
            when {
                legacyWpm == null -> RsvpConfig().tempoMsPerWord
                legacyWpm <= 0 -> RsvpConfig().tempoMsPerWord
                else -> (60_000.0 / legacyWpm.toDouble()).toLong().coerceAtLeast(10L)
            }
        }).coerceAtLeast(10L)
        val derivedBaseWpm = (60_000.0 / tempoMsPerWord.toDouble()).toInt().coerceAtLeast(1)

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
            enablePhraseChunking = prefs[keys.enablePhraseChunking] ?: RsvpConfig().enablePhraseChunking,
            maxWordsPerUnit = prefs[keys.maxWordsPerUnit] ?: RsvpConfig().maxWordsPerUnit,
            maxCharsPerUnit = prefs[keys.maxCharsPerUnit] ?: RsvpConfig().maxCharsPerUnit,
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
            parentheticalMultiplier = prefs[keys.parentheticalMultiplier] ?: RsvpConfig().parentheticalMultiplier,
            dialogueMultiplier = prefs[keys.dialogueMultiplier] ?: RsvpConfig().dialogueMultiplier,
            smoothingAlpha = prefs[keys.smoothingAlpha] ?: RsvpConfig().smoothingAlpha,
            maxSpeedupFactor = prefs[keys.maxSpeedupFactor] ?: RsvpConfig().maxSpeedupFactor,
            maxSlowdownFactor = prefs[keys.maxSlowdownFactor] ?: RsvpConfig().maxSlowdownFactor,
            rampUpFrames = prefs[keys.rampUpFrames] ?: RsvpConfig().rampUpFrames,
            rampDownFrames = prefs[keys.rampDownFrames] ?: RsvpConfig().rampDownFrames,
            wordsPerFrame = prefs[keys.wordsPerFrame] ?: RsvpConfig().wordsPerFrame,
            maxChunkLength = prefs[keys.maxChunkLength] ?: RsvpConfig().maxChunkLength,
            punctuationPauseFactor = prefs[keys.punctuationPause] ?: RsvpConfig().punctuationPauseFactor,
            longWordMultiplier = prefs[keys.longWordMultiplier] ?: RsvpConfig().longWordMultiplier,
            orpEnabled = prefs[keys.orpEnabled] ?: RsvpConfig().orpEnabled,
            startDelayMs = prefs[keys.startDelayMs] ?: RsvpConfig().startDelayMs,
            endDelayMs = prefs[keys.endDelayMs] ?: RsvpConfig().endDelayMs
        )
    }
}

private object PrefKeys {
    val tempoMsPerWord = longPreferencesKey("tempo_ms_per_word")
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
    val readerFontSize = floatPreferencesKey("reader_font_size")
    val readerTheme = stringPreferencesKey("reader_theme")
    val invertedScroll = booleanPreferencesKey("inverted_scroll")
    val rsvpFontSize = floatPreferencesKey("rsvp_font_size")
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
