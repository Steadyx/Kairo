package com.kairo.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpProfileDefaultsTest {
    @Test
    fun userPreferencesDefaultsUseBalancedProfile() {
        val defaults = UserPreferences()
        val balanced = RsvpProfile.BALANCED.defaultConfig()

        assertEquals(RsvpProfileIds.builtIn(RsvpProfile.BALANCED), defaults.rsvpSelectedProfileId)
        assertEquals(balanced, defaults.rsvpConfig)
        assertEquals(balanced.tempoMsPerWord, defaults.rsvpTempoMsPerWord)
    }

    @Test
    fun userPreferencesStartWithSaneFontSizes() {
        val defaults = UserPreferences()

        assertEquals(18f, defaults.readerFontSizeSp, 0.0f)
        assertEquals(28f, defaults.rsvpFontSizeSp, 0.0f)
    }

    @Test
    fun comprehensionProfilesUseConservativePhraseChunking() {
        val chunkedProfiles = setOf(RsvpProfile.BALANCED, RsvpProfile.CHILL, RsvpProfile.NARRATIVE, RsvpProfile.FLOW, RsvpProfile.SPRINT)
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertEquals(
                "Unexpected phrase chunking default for ${profile.name}",
                profile in chunkedProfiles,
                config.enablePhraseChunking,
            )
        }
    }

    @Test
    fun builtInProfilesDisableContextAssistByDefault() {
        assertEquals(RsvpContextAssistMode.OFF, RsvpConfig().contextAssistMode)
        RsvpProfile.entries.forEach { profile ->
            assertEquals(
                "Expected context assist off for ${profile.name}",
                RsvpContextAssistMode.OFF,
                profile.defaultConfig().contextAssistMode,
            )
        }
    }

    @Test
    fun builtInProfilesEnablePunctuationLandingByDefault() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue(
                "Expected punctuation landing on for ${profile.name}",
                config.usePunctuationLandingHold,
            )
        }
    }

    @Test
    fun balancedProfileStartsWithBreathablePunctuation() {
        val config = RsvpProfile.BALANCED.defaultConfig()

        assertTrue("Expected comma breath to be noticeable", config.commaPauseMs >= 165L)
        assertTrue("Expected semicolon breath to exceed commas", config.semicolonPauseMs >= 260L)
        assertTrue("Expected full stops to settle", config.periodPauseMs >= 330L)
        assertTrue("Expected expressive sentence marks to settle", config.sentenceEndPauseMs >= 350L)
        assertTrue("Expected paragraphs to create a real reset", config.paragraphPauseMs >= 430L)
        assertTrue("Explicit punctuation should not receive a second global boost", config.punctuationPauseFactor == 1.0)
        assertTrue("Expected dialogue punctuation to stay readable", config.dialoguePunctuationScale >= 0.94)
        assertFalse("Expected first-run parentheticals to stay readable", config.useParentheticalAside)
    }

    @Test
    fun builtInProfilesLeanIntoPunctuationWithoutLosingTheirSpeedShape() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue("Expected readable comma breath for ${profile.name}", config.commaPauseMs >= 110L)
            assertTrue(
                "Expected semicolon to be closer to a stop than a comma for ${profile.name}",
                config.semicolonPauseMs >= (config.commaPauseMs * 1.45).toLong(),
            )
            assertTrue(
                "Expected ellipses/sentence timing to have a real floor for ${profile.name}",
                config.minPauseScale >= 0.82,
            )
            assertTrue(
                "Expected explicit punctuation timing for ${profile.name}",
                config.punctuationPauseFactor == 1.0,
            )
            assertTrue(
                "Expected dialogue punctuation not to collapse for ${profile.name}",
                config.dialoguePunctuationScale >= 0.92,
            )
            assertFalse(
                "Expected parentheticals to remain readable by default for ${profile.name}",
                config.useParentheticalAside,
            )
        }
    }

    @Test
    fun builtInProfilesMaintainReadablePauseHierarchy() {
        RsvpProfile.entries.forEach { profile ->
            val config = profile.defaultConfig()
            assertTrue("Expected comma pause for ${profile.name}", config.commaPauseMs > 0L)
            assertTrue(
                "Expected sentence pauses to exceed comma pauses for ${profile.name}",
                config.periodPauseMs > config.commaPauseMs &&
                    config.sentenceEndPauseMs > config.commaPauseMs,
            )
            assertTrue(
                "Expected expressive sentence marks to hold at least as long as periods for ${profile.name}",
                config.sentenceEndPauseMs >= config.periodPauseMs,
            )
            assertTrue(
                "Expected semicolons to sit between commas and periods for ${profile.name}",
                config.semicolonPauseMs > config.commaPauseMs &&
                    config.semicolonPauseMs < config.periodPauseMs,
            )
            assertTrue(
                "Expected colons and dashes to create clause holds for ${profile.name}",
                config.colonPauseMs > config.commaPauseMs &&
                    config.dashPauseMs > config.commaPauseMs,
            )
            assertTrue(
                "Expected soft punctuation to remain lighter than clause punctuation for ${profile.name}",
                config.quotePauseMs < config.commaPauseMs &&
                    config.parenthesesPauseMs < config.semicolonPauseMs,
            )
            assertTrue(
                "Expected paragraph pauses to exceed sentence pauses for ${profile.name}",
                config.paragraphPauseMs > config.sentenceEndPauseMs,
            )
            assertTrue(
                "Expected long-word floor to exceed base floor for ${profile.name}",
                config.longWordMinMs > config.minWordMs,
            )
            assertTrue(
                "Expected pause floor to stay within a readable range for ${profile.name}",
                config.minPauseScale in 0.65..0.9,
            )
        }
    }

    @Test
    fun profilesHaveDistinctReadingRoles() {
        val balanced = RsvpProfile.BALANCED.defaultConfig()
        val narrative = RsvpProfile.NARRATIVE.defaultConfig()
        val focus = RsvpProfile.FOCUS.defaultConfig()
        val flow = RsvpProfile.FLOW.defaultConfig()
        val skim = RsvpProfile.SPRINT.defaultConfig()
        val study = RsvpProfile.STUDY.defaultConfig()
        val chill = RsvpProfile.CHILL.defaultConfig()

        assertTrue(narrative.prosodyStrength > balanced.prosodyStrength)
        assertTrue(chill.paragraphPauseMs > narrative.paragraphPauseMs)
        assertTrue(chill.smoothingAlpha < balanced.smoothingAlpha)
        assertFalse(focus.enablePhraseChunking)
        assertFalse(focus.useProsodyPacing)
        assertFalse(focus.useFocalStress)
        assertFalse(focus.useAnticipatoryLanding)
        assertTrue(focus.maxSlowdownFactor < balanced.maxSlowdownFactor)
        assertEquals(3, flow.maxWordsPerUnit)
        assertTrue(flow.maxCharsPerUnit > narrative.maxCharsPerUnit)
        assertTrue(skim.enablePhraseChunking)
        assertFalse(skim.useProsodyPacing)
        assertTrue(skim.complexWordHoldMs < focus.complexWordHoldMs)
        assertFalse(study.enablePhraseChunking)
        assertFalse(study.useFocalStress)
        assertTrue(study.longWordMinMs > balanced.longWordMinMs)
        assertTrue(study.maxChunkLength >= 32)
        assertTrue(study.adaptiveDifficultyMaxHoldMs > balanced.adaptiveDifficultyMaxHoldMs)
        assertEquals(7, RsvpProfile.entries.map { it.defaultConfig().profileCadenceIdentity() }.toSet().size)
    }

    @Test
    fun selectingCadencePreservesSpeedAndVisualPreferences() {
        val current = RsvpConfig().copy(
            tempoMsPerWord = 230L,
            baseWpm = 260,
            contextAssistMode = RsvpContextAssistMode.SENTENCE_TICKER,
            blinkMode = BlinkMode.ADAPTIVE,
            orpEnabled = false,
            orpHighlightEnabled = false,
            orpGuideEnabled = true,
            orpGuideBrightness = 0.65,
            orpGuideThickness = 2.0,
        )
        RsvpProfile.entries.forEach { profile ->
            val preset = profile.defaultConfig()
            val selected = preset.withReaderPreferencesFrom(current)
            assertEquals(preset.profileCadenceIdentity(), selected.profileCadenceIdentity())
            assertEquals(current, current.profileCadenceIdentity().withReaderPreferencesFrom(selected))
        }
    }

    @Test
    fun builtInProfilesDefineDistinctProsodyStrengths() {
        val distinctProsodyStrengths =
            RsvpProfile.entries
                .map { it.defaultConfig().prosodyStrength }
                .toSet()
        assertTrue(
            "Expected profiles to provide meaningful cadence variation",
            distinctProsodyStrengths.size >= 4,
        )
    }
}
