package com.kairo.reader.data.preferences

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun updateHasSeenStartingTutorial(seen: Boolean)

    suspend fun updateRsvpConfig(updater: (RsvpConfig) -> RsvpConfig)

    suspend fun updateRsvpTempoMsPerWord(tempoMsPerWord: Long)

    suspend fun selectRsvpProfile(profileId: String)

    suspend fun saveRsvpCustomProfile(
        name: String,
        config: RsvpConfig,
    )

    suspend fun deleteRsvpCustomProfile(profileId: String)

    suspend fun updateUnlockExtremeSpeed(enabled: Boolean)

    suspend fun updateFontSize(size: Float)

    suspend fun updateReaderTextBrightness(brightness: Float)

    suspend fun updateTheme(theme: String)

    suspend fun updateAppearance(
        customTheme: com.kairo.reader.core.model.CustomTheme,
        readerFont: RsvpFontFamily,
        interfaceFont: RsvpFontFamily,
        timedFont: RsvpFontFamily,
        theme: com.kairo.reader.core.model.ReaderTheme,
    )

    suspend fun updateInvertedScroll(enabled: Boolean)

    suspend fun updateRsvpFontSize(size: Float)

    suspend fun updateRsvpTextBrightness(brightness: Float)

    suspend fun updateRsvpFontWeight(weight: RsvpFontWeight)

    suspend fun updateRsvpFontFamily(family: RsvpFontFamily)

    suspend fun updateRsvpVerticalBias(bias: Float)

    suspend fun updateRsvpHorizontalBias(bias: Float)

    suspend fun updateRsvpPositioningGridEnabled(enabled: Boolean)

    suspend fun updateRsvpPositioningGridSnap(snap: Float)

    suspend fun updateTimedReadingMode(mode: TimedReadingMode)

    suspend fun updateBionicFixationStrength(strength: Float)

    suspend fun updateBionicHighlightStrength(strength: Float)

    suspend fun updateBionicFontSize(size: Float)

    suspend fun updateBionicTextBrightness(brightness: Float)

    suspend fun updateFocusModeEnabled(enabled: Boolean)

    suspend fun updateFocusHideStatusBar(enabled: Boolean)

    suspend fun updateFocusPauseNotifications(enabled: Boolean)

    suspend fun updateFocusApplyInReader(enabled: Boolean)

    suspend fun updateFocusApplyInRsvp(enabled: Boolean)

    suspend fun updateWeeklyReadingGoalMinutes(minutes: Int)

    suspend fun updateMomentumResetCutoffAt(cutoffAt: Long)

    suspend fun reset()
}
