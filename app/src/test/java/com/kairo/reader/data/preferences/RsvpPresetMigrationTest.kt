package com.kairo.reader.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.defaultConfig
import com.kairo.reader.core.model.withReaderPreferencesFrom
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpPresetMigrationTest {
    private val codec = RsvpConfigPreferenceCodec(PrefKeys, RsvpProfileJsonCodec())

    @Test
    fun upgradesEveryBuiltInWhileKeepingReaderPreferences() {
        RsvpProfile.entries.forEach { profile ->
            val prefs = mutablePreferencesOf(PrefKeys.rsvpProfile to RsvpProfileIds.builtIn(profile))
            codec.writeRsvpConfig(
                prefs,
                RsvpConfig().copy(
                    tempoMsPerWord = 230L,
                    commaPauseMs = 999L,
                    contextAssistMode = RsvpContextAssistMode.FULL_CLAUSE,
                    blinkMode = BlinkMode.ADAPTIVE,
                    orpEnabled = false,
                    orpHighlightEnabled = false,
                    orpGuideEnabled = true,
                    orpGuideBrightness = 0.65,
                    orpGuideThickness = 2.0,
                )
            )
            val before = codec.readRsvpConfig(prefs)
            migrateRsvpPresets(prefs, codec)
            assertEquals(profile.defaultConfig().withReaderPreferencesFrom(before), codec.readRsvpConfig(prefs))
            assertEquals(RsvpProfileIds.builtIn(profile), prefs[PrefKeys.rsvpProfile])
        }
    }

    @Test
    fun savedAndUnsavedCustomCadencesAreUntouched() {
        listOf("user:mine", RsvpProfileIds.CUSTOM_UNSAVED).forEach { id ->
            val prefs = mutablePreferencesOf(PrefKeys.rsvpProfile to id)
            codec.writeRsvpConfig(prefs, RsvpConfig().copy(commaPauseMs = 999L))
            prefs[PrefKeys.customRsvpProfilesJson] = "saved profiles must remain byte for byte"
            val before = prefs.asMap()
            migrateRsvpPresets(prefs, codec)
            assertEquals(before, prefs.asMap().filterKeys { it != PrefKeys.rsvpPresetVersion })
        }
    }

    @Test
    fun migrationRunsOnlyOnceAndFreshInstallGetsBalanced() {
        val prefs = mutablePreferencesOf()
        migrateRsvpPresets(prefs, codec)
        val actual = codec.readRsvpConfig(prefs)
        assertEquals(RsvpProfile.BALANCED.defaultConfig().withReaderPreferencesFrom(actual), actual)
        prefs[PrefKeys.commaPauseMs] = 777L
        val before = prefs.asMap()
        migrateRsvpPresets(prefs, codec)
        assertEquals(before, prefs.asMap())
    }
}
