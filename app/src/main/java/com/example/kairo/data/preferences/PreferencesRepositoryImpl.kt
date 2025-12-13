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
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class PreferencesRepositoryImpl(
    private val context: Context
) : PreferencesRepository {

    private val keys = PrefKeys

    override val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            rsvpConfig = RsvpConfig(
                baseWpm = prefs[keys.baseWpm] ?: RsvpConfig().baseWpm,
                wordsPerFrame = prefs[keys.wordsPerFrame] ?: RsvpConfig().wordsPerFrame,
                maxChunkLength = prefs[keys.maxChunkLength] ?: RsvpConfig().maxChunkLength,
                punctuationPauseFactor = prefs[keys.punctuationPause] ?: RsvpConfig().punctuationPauseFactor,
                paragraphPauseMs = prefs[keys.paragraphPauseMs] ?: RsvpConfig().paragraphPauseMs,
                longWordMultiplier = prefs[keys.longWordMultiplier] ?: RsvpConfig().longWordMultiplier,
                orpEnabled = prefs[keys.orpEnabled] ?: RsvpConfig().orpEnabled,
                startDelayMs = prefs[keys.startDelayMs] ?: RsvpConfig().startDelayMs,
                endDelayMs = prefs[keys.endDelayMs] ?: RsvpConfig().endDelayMs
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
            prefs[keys.baseWpm] = updated.baseWpm
            prefs[keys.wordsPerFrame] = updated.wordsPerFrame
            prefs[keys.maxChunkLength] = updated.maxChunkLength
            prefs[keys.punctuationPause] = updated.punctuationPauseFactor
            prefs[keys.paragraphPauseMs] = updated.paragraphPauseMs
            prefs[keys.longWordMultiplier] = updated.longWordMultiplier
            prefs[keys.orpEnabled] = updated.orpEnabled
            prefs[keys.startDelayMs] = updated.startDelayMs
            prefs[keys.endDelayMs] = updated.endDelayMs
        }
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

    private fun readConfig(prefs: Preferences): RsvpConfig = RsvpConfig(
        baseWpm = prefs[keys.baseWpm] ?: RsvpConfig().baseWpm,
        wordsPerFrame = prefs[keys.wordsPerFrame] ?: RsvpConfig().wordsPerFrame,
        maxChunkLength = prefs[keys.maxChunkLength] ?: RsvpConfig().maxChunkLength,
        punctuationPauseFactor = prefs[keys.punctuationPause] ?: RsvpConfig().punctuationPauseFactor,
        paragraphPauseMs = prefs[keys.paragraphPauseMs] ?: RsvpConfig().paragraphPauseMs,
        longWordMultiplier = prefs[keys.longWordMultiplier] ?: RsvpConfig().longWordMultiplier,
        orpEnabled = prefs[keys.orpEnabled] ?: RsvpConfig().orpEnabled,
        startDelayMs = prefs[keys.startDelayMs] ?: RsvpConfig().startDelayMs,
        endDelayMs = prefs[keys.endDelayMs] ?: RsvpConfig().endDelayMs
    )
}

private object PrefKeys {
    val baseWpm = intPreferencesKey("base_wpm")
    val wordsPerFrame = intPreferencesKey("words_per_frame")
    val maxChunkLength = intPreferencesKey("max_chunk_length")
    val punctuationPause = doublePreferencesKey("punctuation_pause_factor")
    val paragraphPauseMs = longPreferencesKey("paragraph_pause_ms")
    val longWordMultiplier = doublePreferencesKey("long_word_multiplier")
    val orpEnabled = booleanPreferencesKey("orp_enabled")
    val startDelayMs = longPreferencesKey("start_delay_ms")
    val endDelayMs = longPreferencesKey("end_delay_ms")
    val readerFontSize = floatPreferencesKey("reader_font_size")
    val readerTheme = stringPreferencesKey("reader_theme")
    val invertedScroll = booleanPreferencesKey("inverted_scroll")
    val rsvpFontSize = floatPreferencesKey("rsvp_font_size")
    val rsvpFontWeight = stringPreferencesKey("rsvp_font_weight")
    val rsvpFontFamily = stringPreferencesKey("rsvp_font_family")
    val rsvpVerticalBias = floatPreferencesKey("rsvp_vertical_bias")
    val focusModeEnabled = booleanPreferencesKey("focus_mode_enabled")
    val focusHideStatusBar = booleanPreferencesKey("focus_hide_status_bar")
    val focusPauseNotifications = booleanPreferencesKey("focus_pause_notifications")
    val focusApplyInReader = booleanPreferencesKey("focus_apply_in_reader")
    val focusApplyInRsvp = booleanPreferencesKey("focus_apply_in_rsvp")
}
