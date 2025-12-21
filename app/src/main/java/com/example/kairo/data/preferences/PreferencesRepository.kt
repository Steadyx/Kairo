package com.example.kairo.data.preferences

import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun updateRsvpConfig(updater: (RsvpConfig) -> RsvpConfig)

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

    suspend fun updateInvertedScroll(enabled: Boolean)

    suspend fun updateRsvpFontSize(size: Float)

    suspend fun updateRsvpTextBrightness(brightness: Float)

    suspend fun updateRsvpFontWeight(weight: RsvpFontWeight)

    suspend fun updateRsvpFontFamily(family: RsvpFontFamily)

    suspend fun updateRsvpVerticalBias(bias: Float)

    suspend fun updateRsvpHorizontalBias(bias: Float)

    suspend fun updateFocusModeEnabled(enabled: Boolean)

    suspend fun updateFocusHideStatusBar(enabled: Boolean)

    suspend fun updateFocusPauseNotifications(enabled: Boolean)

    suspend fun updateFocusApplyInReader(enabled: Boolean)

    suspend fun updateFocusApplyInRsvp(enabled: Boolean)

    suspend fun reset()
}
