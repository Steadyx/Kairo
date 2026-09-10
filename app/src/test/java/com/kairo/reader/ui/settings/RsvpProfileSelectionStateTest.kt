package com.kairo.reader.ui.settings

import com.kairo.reader.core.model.BlinkMode
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpContextAssistMode
import com.kairo.reader.core.model.RsvpCustomProfile
import com.kairo.reader.core.model.RsvpProfile
import com.kairo.reader.core.model.RsvpProfileIds
import com.kairo.reader.core.model.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RsvpProfileSelectionStateTest {
    @Test
    fun phraseChunkWordLimitStaysWithinRsvpFriendlyRange() {
        assertEquals(2, coercePhraseChunkWordLimit(1))
        assertEquals(2, coercePhraseChunkWordLimit(2))
        assertEquals(3, coercePhraseChunkWordLimit(3))
        assertEquals(3, coercePhraseChunkWordLimit(4))
    }

    @Test
    fun builtInProfileWithTweakedConfigShowsUnsavedCustomState() {
        val state =
            resolveRsvpProfileSelectionState(
                selectedProfileId = RsvpProfileIds.builtIn(RsvpProfile.NARRATIVE),
                customProfiles = emptyList(),
                profileComparisonConfig = RsvpProfile.NARRATIVE.defaultConfig().copy(minWordMs = 77L),
            )

        assertEquals(RsvpProfileIds.CUSTOM_UNSAVED, state.effectiveSelectedProfileId)
        assertNull(state.selectedBuiltIn)
        assertNull(state.selectedCustom)
    }

    @Test
    fun matchingBuiltInProfileRemainsSelected() {
        val state =
            resolveRsvpProfileSelectionState(
                selectedProfileId = RsvpProfileIds.builtIn(RsvpProfile.NARRATIVE),
                customProfiles = emptyList(),
                profileComparisonConfig = RsvpProfile.NARRATIVE.defaultConfig(),
            )

        assertEquals(RsvpProfileIds.builtIn(RsvpProfile.NARRATIVE), state.effectiveSelectedProfileId)
        assertEquals(RsvpProfile.NARRATIVE, state.selectedBuiltIn)
        assertNull(state.selectedCustom)
    }

    @Test
    fun builtInProfileWithOnlyTempoChangeRemainsSelected() {
        val state =
            resolveRsvpProfileSelectionState(
                selectedProfileId = RsvpProfileIds.builtIn(RsvpProfile.NARRATIVE),
                customProfiles = emptyList(),
                profileComparisonConfig =
                RsvpProfile.NARRATIVE.defaultConfig().copy(
                    tempoMsPerWord = 88L,
                    baseWpm = 681,
                    contextAssistMode = RsvpContextAssistMode.SENTENCE_TICKER,
                    blinkMode = BlinkMode.ADAPTIVE,
                    orpGuideEnabled = true,
                ),
            )

        assertEquals(RsvpProfileIds.builtIn(RsvpProfile.NARRATIVE), state.effectiveSelectedProfileId)
        assertEquals(RsvpProfile.NARRATIVE, state.selectedBuiltIn)
        assertNull(state.selectedCustom)
    }

    @Test
    fun customProfileWithTweakedConfigShowsUnsavedCustomState() {
        val customProfile =
            RsvpCustomProfile(
                id = "user:test",
                name = "My Flow",
                config = RsvpConfig(),
                updatedAtMs = 1L,
            )

        val state =
            resolveRsvpProfileSelectionState(
                selectedProfileId = customProfile.id,
                customProfiles = listOf(customProfile),
                profileComparisonConfig = customProfile.config.copy(sentenceEndPauseMs = 333L),
            )

        assertEquals(RsvpProfileIds.CUSTOM_UNSAVED, state.effectiveSelectedProfileId)
        assertNull(state.selectedBuiltIn)
        assertNull(state.selectedCustom)
    }

    @Test
    fun customProfileWithOnlyTempoChangeRemainsSelected() {
        val customProfile =
            RsvpCustomProfile(
                id = "user:test",
                name = "My Flow",
                config = RsvpConfig(),
                updatedAtMs = 1L,
            )

        val state =
            resolveRsvpProfileSelectionState(
                selectedProfileId = customProfile.id,
                customProfiles = listOf(customProfile),
                profileComparisonConfig = customProfile.config.copy(tempoMsPerWord = 72L, baseWpm = 833),
            )

        assertEquals(customProfile.id, state.effectiveSelectedProfileId)
        assertNull(state.selectedBuiltIn)
        assertEquals(customProfile, state.selectedCustom)
    }
}
