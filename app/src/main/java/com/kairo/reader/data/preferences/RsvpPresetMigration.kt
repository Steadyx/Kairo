package com.kairo.reader.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.model.withReaderPreferencesFrom

private const val CURRENT_RSVP_PRESET_VERSION = 2

/** Run inside the DataStore transaction so a stale selection cannot replace a custom cadence. */
internal fun migrateRsvpPresets(prefs: MutablePreferences, codec: RsvpConfigPreferenceCodec) {
    if ((prefs[PrefKeys.rsvpPresetVersion] ?: 0) >= CURRENT_RSVP_PRESET_VERSION) return
    val selectedId = prefs[PrefKeys.rsvpProfile]?.let(::normalizeRsvpProfileId)
        ?: RsvpProfileIds.builtIn(RsvpProfile.BALANCED)
    val builtIn = RsvpProfileIds.parseBuiltIn(selectedId)
    if (builtIn != null) {
        val current = codec.readRsvpConfig(prefs)
        codec.writeRsvpConfig(
            prefs,
            builtIn.defaultConfig().withReaderPreferencesFrom(current),
        )
    }
    prefs[PrefKeys.rsvpPresetVersion] = CURRENT_RSVP_PRESET_VERSION
}
