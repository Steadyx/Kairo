package com.kairo.reader.data.preferences

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.model.profileCadenceIdentity
import com.kairo.reader.core.model.withReaderPreferencesFrom
import com.kairo.reader.core.rsvp.RsvpSpeedControl
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val legacyBaseWpmKey = intPreferencesKey("base_wpm")
internal const val MIN_TEXT_BRIGHTNESS = 0.55f
internal const val MAX_TEXT_BRIGHTNESS = 1.0f
internal const val MIN_RSVP_VERTICAL_BIAS = -0.7f
internal const val MAX_RSVP_VERTICAL_BIAS = 0.7f
internal const val MIN_RSVP_HORIZONTAL_BIAS = -0.6f
internal const val MAX_RSVP_HORIZONTAL_BIAS = 0.6f
internal const val MIN_BIONIC_FIXATION_STRENGTH = 0.30f
internal const val MAX_BIONIC_FIXATION_STRENGTH = 0.70f
internal const val MIN_BIONIC_HIGHLIGHT_STRENGTH = 0.08f
internal const val MAX_BIONIC_HIGHLIGHT_STRENGTH = 0.32f
internal const val MIN_BIONIC_FONT_SIZE_SP = 18f
internal const val MAX_BIONIC_FONT_SIZE_SP = 40f
internal const val MIN_WEEKLY_READING_GOAL_MINUTES = 30
internal const val MAX_WEEKLY_READING_GOAL_MINUTES = 1_400

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

internal fun RsvpConfig.normalizedNaturalFlowMultipliers(
    defaults: RsvpConfig = RsvpConfig(),
): RsvpConfig =
    copy(
        focalSupportCompression =
        normalizedFiniteMultiplier(
            value = focalSupportCompression,
            fallback = defaults.focalSupportCompression,
            minValue = 0.75,
            maxValue = 1.0,
        ),
        dialoguePunctuationScale =
        normalizedFiniteMultiplier(
            value = dialoguePunctuationScale,
            fallback = defaults.dialoguePunctuationScale,
            minValue = 0.5,
            maxValue = 1.0,
        ),
        parentheticalAsideMultiplier =
        normalizedFiniteMultiplier(
            value = parentheticalAsideMultiplier,
            fallback = defaults.parentheticalAsideMultiplier,
            minValue = 0.5,
            maxValue = 1.0,
        ),
    )

private fun normalizedFiniteMultiplier(
    value: Double,
    fallback: Double,
    minValue: Double,
    maxValue: Double,
): Double =
    value
        .takeIf { it.isFinite() }
        ?.coerceIn(minValue, maxValue)
        ?: fallback.coerceIn(minValue, maxValue)

internal fun readerThemeForNightMode(uiMode: Int): ReaderTheme =
    when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> ReaderTheme.DARK
        else -> ReaderTheme.LIGHT
    }

internal fun timedReadingModeFromStored(
    value: String?,
    fallback: TimedReadingMode = TimedReadingMode.RSVP,
): TimedReadingMode =
    value
        ?.let { stored -> runCatching { TimedReadingMode.valueOf(stored) }.getOrNull() }
        ?: fallback

// The repository intentionally mirrors the explicit preference update API; private codecs and
// mappers remain split into their own files.
@Suppress("TooManyFunctions")
class PreferencesRepositoryImpl(private val context: Context,) : PreferencesRepository {
    private val keys = PrefKeys
    private val migrationMutex = Mutex()

    @Volatile private var migrationsComplete = false
    private val profileJsonCodec =
        RsvpProfileJsonCodec { error ->
            Log.w(TAG, "Ignoring malformed custom RSVP profile data", error)
        }
    private val configCodec = RsvpConfigPreferenceCodec(keys, profileJsonCodec)
    private val userPreferencesMapper =
        UserPreferencesMapper(context, keys, profileJsonCodec, configCodec)

    override val preferences: Flow<UserPreferences> =
        flow {
            ensureMigrations()
            emitAll(context.dataStore.data.map(userPreferencesMapper::map))
        }

    private suspend fun ensureMigrations() {
        if (migrationsComplete) return
        migrationMutex.withLock {
            if (migrationsComplete) return
            migrateLegacyBaseWpmIfNeeded(context.dataStore.data.first())
            migrateRsvpSpeedCurveIfNeeded(context.dataStore.data.first())
            context.dataStore.edit { migrateRsvpPresets(it, configCodec) }
            migrationsComplete = true
        }
    }

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

    private suspend fun migrateRsvpSpeedCurveIfNeeded(prefs: Preferences) {
        if (prefs.contains(legacyBaseWpmKey)) return
        val storedVersion = prefs[keys.rsvpSpeedCurveVersion] ?: 1
        if (storedVersion >= RsvpSpeedControl.SPEED_CURVE_VERSION) return

        context.dataStore.edit { mutable ->
            val storedTempo = mutable[keys.tempoMsPerWord]
            if (storedTempo != null) {
                val minTempoMs =
                    if (mutable[keys.unlockExtremeSpeed] == true ||
                        storedTempo < RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD
                    ) {
                        RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD
                    } else {
                        RsvpSpeedControl.SAFE_MIN_TEMPO_MS_PER_WORD
                    }
                mutable[keys.tempoMsPerWord] =
                    RsvpSpeedControl.recalibrateLegacyTempoMs(
                        tempoMsPerWord = storedTempo,
                        minTempoMsPerWord = minTempoMs,
                    )
            }
            mutable[keys.rsvpSpeedCurveVersion] = RsvpSpeedControl.SPEED_CURVE_VERSION
        }
    }

    override suspend fun updateRsvpConfig(updater: (RsvpConfig) -> RsvpConfig) {
        ensureMigrations()
        context.dataStore.edit { prefs ->
            val current = configCodec.readRsvpConfig(prefs)
            val timingInfo = configCodec.readTimingInfo(prefs, current)
            val updated =
                updater(current)
                    .withTiming(timingInfo)
            if (updated.profileCadenceIdentity() != current.profileCadenceIdentity()) {
                prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
            }
            configCodec.writeRsvpConfig(prefs, updated, includeTiming = false)
        }
    }

    override suspend fun updateRsvpTempoMsPerWord(tempoMsPerWord: Long) {
        ensureMigrations()
        context.dataStore.edit { prefs ->
            prefs[keys.tempoMsPerWord] =
                tempoMsPerWord.coerceAtLeast(RsvpSpeedControl.EXTREME_MIN_TEMPO_MS_PER_WORD)
            prefs[keys.rsvpSpeedCurveVersion] = RsvpSpeedControl.SPEED_CURVE_VERSION
            prefs.remove(legacyBaseWpmKey)
        }
    }

    override suspend fun updateHasSeenStartingTutorial(seen: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.hasSeenStartingTutorial] = seen }
    }

    override suspend fun selectRsvpProfile(profileId: String) {
        ensureMigrations()
        context.dataStore.edit { prefs ->
            val current = configCodec.readRsvpConfig(prefs)
            val normalized = normalizeRsvpProfileId(profileId)
            when {
                normalized == RsvpProfileIds.CUSTOM_UNSAVED -> {
                    prefs[keys.rsvpProfile] = RsvpProfileIds.CUSTOM_UNSAVED
                }

                RsvpProfileIds.isBuiltIn(normalized) -> {
                    val builtIn = RsvpProfileIds.parseBuiltIn(normalized) ?: RsvpProfile.BALANCED
                    prefs[keys.rsvpProfile] = RsvpProfileIds.builtIn(builtIn)
                    configCodec.writeRsvpConfig(
                        prefs,
                        builtIn.defaultConfig().withReaderPreferencesFrom(current),
                    )
                }

                RsvpProfileIds.isCustom(normalized) -> {
                    val profiles = profileJsonCodec.parseCustomProfiles(prefs[keys.customRsvpProfilesJson])
                    val match = profiles.firstOrNull { it.id == normalized }
                    prefs[keys.rsvpProfile] = normalized
                    if (match != null) {
                        configCodec.writeRsvpConfig(
                            prefs,
                            match.config.withReaderPreferencesFrom(current),
                        )
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
        val trimmedName = name.trim().take(MAX_CUSTOM_PROFILE_NAME_LENGTH)
        if (trimmedName.isBlank()) return
        ensureMigrations()

        context.dataStore.edit { prefs ->
            val existing = profileJsonCodec.parseCustomProfiles(prefs[keys.customRsvpProfilesJson]).toMutableList()
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
            prefs[keys.customRsvpProfilesJson] = profileJsonCodec.encodeCustomProfiles(existing)
            prefs[keys.rsvpProfile] = id
        }
    }

    override suspend fun deleteRsvpCustomProfile(profileId: String) {
        if (!RsvpProfileIds.isCustom(profileId)) return
        context.dataStore.edit { prefs ->
            val existing = profileJsonCodec.parseCustomProfiles(prefs[keys.customRsvpProfilesJson]).toMutableList()
            val removed = existing.removeAll { it.id == profileId }
            if (!removed) return@edit
            prefs[keys.customRsvpProfilesJson] = profileJsonCodec.encodeCustomProfiles(existing)
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
            prefs[keys.rsvpVerticalBias] =
                bias.coerceIn(MIN_RSVP_VERTICAL_BIAS, MAX_RSVP_VERTICAL_BIAS)
        }
    }

    override suspend fun updateRsvpHorizontalBias(bias: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.rsvpHorizontalBias] =
                bias.coerceIn(MIN_RSVP_HORIZONTAL_BIAS, MAX_RSVP_HORIZONTAL_BIAS)
        }
    }

    override suspend fun updateRsvpPositioningGridEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[keys.rsvpPositioningGridEnabled] = enabled }
    }

    override suspend fun updateRsvpPositioningGridSnap(snap: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.rsvpPositioningGridSnap] = snap.coerceIn(0f, 1f)
        }
    }

    override suspend fun updateTimedReadingMode(mode: TimedReadingMode) {
        context.dataStore.edit { prefs -> prefs[keys.timedReadingMode] = mode.name }
    }

    override suspend fun updateBionicFixationStrength(strength: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.bionicFixationStrength] =
                strength.coerceIn(
                    MIN_BIONIC_FIXATION_STRENGTH,
                    MAX_BIONIC_FIXATION_STRENGTH,
                )
        }
    }

    override suspend fun updateBionicHighlightStrength(strength: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.bionicHighlightStrength] =
                strength.coerceIn(
                    MIN_BIONIC_HIGHLIGHT_STRENGTH,
                    MAX_BIONIC_HIGHLIGHT_STRENGTH,
                )
        }
    }

    override suspend fun updateBionicFontSize(size: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.bionicFontSize] =
                size.coerceIn(MIN_BIONIC_FONT_SIZE_SP, MAX_BIONIC_FONT_SIZE_SP)
        }
    }

    override suspend fun updateBionicTextBrightness(brightness: Float) {
        context.dataStore.edit { prefs ->
            prefs[keys.bionicTextBrightness] =
                brightness.coerceIn(MIN_TEXT_BRIGHTNESS, MAX_TEXT_BRIGHTNESS)
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

    override suspend fun updateWeeklyReadingGoalMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[keys.weeklyReadingGoalMinutes] = minutes.coerceIn(
                MIN_WEEKLY_READING_GOAL_MINUTES,
                MAX_WEEKLY_READING_GOAL_MINUTES,
            )
        }
    }

    override suspend fun updateMomentumResetCutoffAt(cutoffAt: Long) {
        context.dataStore.edit { prefs -> prefs[keys.momentumResetCutoffAt] = cutoffAt }
    }

    override suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}

private const val MAX_CUSTOM_PROFILE_NAME_LENGTH = 32

private const val TAG = "PreferencesRepository"
