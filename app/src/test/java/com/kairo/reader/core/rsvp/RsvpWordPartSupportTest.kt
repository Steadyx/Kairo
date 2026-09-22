package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.splitTokenForRsvp
import com.kairo.reader.core.rsvp.analysis.ReadingDemandAnalyzer
import com.kairo.reader.core.rsvp.analysis.RsvpWordPartSupport
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpWordPartSupportTest {
    private val config = RsvpConfig(maxChunkLength = 32, subwordChunkPauseMs = 35L)
    private val english = RsvpLanguagePolicy.ENGLISH

    @Test
    fun familiarAndShortUncertainWordsStayWhole() {
        val bases = listOf(
            "instructor", "teacher", "student", "instruction", "whispered", "distinguish",
            "comfortable", "everything", "conversation", "information", "international",
            "professional", "opportunity", "university", "explanation", "measurement", "Mavik", "Zalorin",
        )
        for (base in bases) {
            for (form in listOf(base, base.uppercase(Locale.ROOT), "$base's")) {
                assertEquals(form, 1, parts(form).size)
            }
        }
    }

    @Test
    fun uncommonFamiliesKeepTheSameDecisionAcrossTheOldTimingCutoff() {
        for ((singular, plural) in listOf("slateboard" to "slateboards", "crucible" to "crucibles")) {
            val before = ReadingDemandAnalyzer.analyze(word(singular), english)
            val after = ReadingDemandAnalyzer.analyze(word(plural), english)
            assertTrue(before.difficulty < 0.68 && after.difficulty > 0.68)
            assertTrue(after.allowanceMs(150.0) > before.allowanceMs(150.0))
            assertTrue(after.allowanceMs(150.0) - before.allowanceMs(150.0) < 10.0)
            assertTrue(parts(singular).size > 1)
            assertFamily(singular, listOf(plural))
        }
    }

    @Test
    fun longStructuredFamiliesKeepTheirPartCountAndBoundariesAcrossInflections() {
        val families = listOf(
            "observation" to "observations",
            "examination" to "examinations",
            "crucible" to "crucibles",
            "dormitory" to "dormitories",
            "alabaster" to "alabasters",
            "representative" to "representatives",
            "responsibility" to "responsibilities",
            "neuroplasticity" to "neuroplasticities",
            "photosynthesis" to "photosyntheses",
            "misrepresentation" to "misrepresentations",
            "misinterpretation" to "misinterpretations",
            "administration" to "administrations",
            "electrocardiography" to "electrocardiographies",
            "unconsciousness" to "unconsciousnesses",
            "counterargument" to "counterarguments",
        )
        for ((base, plural) in families) {
            assertTrue(base, parts(base).size > 1)
            assertFamily(base, listOf(plural, "$base's", "$base’s", "$plural'", base.uppercase(Locale.ROOT)))
        }
    }

    @Test
    fun commonFamiliesAndVerbFormsDoNotBecomeDifficultBecauseTheirFormIsLessFrequent() {
        for ((base, plural) in listOf(
            "instructor" to "instructors",
            "university" to "universities",
            "explanation" to "explanations",
            "measurement" to "measurements",
        )) {
            assertEquals(base, 1, parts(base).size)
            assertFamily(base, listOf(plural, "$base's", "$plural'", plural.uppercase(Locale.ROOT)))
        }
        for (form in listOf("whispered", "whispering", "deepening", "retrieving", "painfully", "nervously", "cleanliness", "unfairness")) {
            assertEquals(form, 1, parts(form).size)
        }
    }

    @Test
    fun uncommonOrUnfamiliarComplexWordsReceiveSupportWithoutNeedingFourteenLetters() {
        for (text in listOf("crucible", "crystalline", "dormitory", "equatorial", "pantomime", "alabaster", "slateboard", "quizzacious")) {
            assertTrue(text, parts(text).size > 1)
        }
    }

    @Test
    fun generatedUnknownFamiliesStayStableAcrossLengthAndPartCountBoundaries() {
        // Exercise missing dictionary entries and the old 6-character and score cliffs over
        // many family shapes, rather than maintaining an exception list for reported words.
        for (length in 7..24) {
            for ((ending, pluralEnding) in listOf("or" to "ors", "ity" to "ities", "ation" to "ations", "ness" to "nesses")) {
                val prefix = "bada".repeat(length).take(length - ending.length)
                val base = prefix + ending
                val plural = prefix + pluralEnding
                assertFamily(base, listOf(plural, "$base's", "$plural'", base.uppercase(Locale.ROOT)))
            }
        }
    }

    @Test
    fun displayPlanIgnoresLegacyDifficultyScoresTempoAndPositiveSupportStrength() {
        for (text in listOf("slateboard", "crucibles", "representative", "neuroplasticity")) {
            val original = word(text)
            val changed = original.copy(syllableCount = 99, frequencyScore = 0.0, complexityMultiplier = 99.0)
            for (tempo in listOf(50L, 150L, 500L)) {
                for (support in listOf(0.0, 0.01, 0.25, 1.0, 2.0)) {
                    val settings = config.copy(tempoMsPerWord = tempo, difficultWordSupport = support)
                    assertEquals(ranges(parts(text)), ranges(RsvpWordPartSupport.split(changed, settings, english)))
                }
            }
        }
    }

    @Test
    fun languageOptOutAndExplicitLimitsKeepTheirConfiguredBehavior() {
        for (policy in RsvpLanguagePolicy.entries.filter { it != english }) {
            for (text in listOf("neuroplasticity", "阅读理解阅读理解阅读理解", "المستشفيات")) {
                val token = word(text)
                assertEquals(splitTokenForRsvp(token, 32, 35L), RsvpWordPartSupport.split(token, config, policy))
            }
        }
        for (text in listOf("1234567890123456", "well-understood", "électromagnétique")) {
            assertEquals(splitTokenForRsvp(word(text), 32, 35L), parts(text))
        }
        for (settings in listOf(config.copy(showDifficultWordParts = false), config.copy(maxChunkLength = 0))) {
            assertEquals(1, RsvpWordPartSupport.split(word("neuroplasticity"), settings, english).size)
        }
        assertTrue(parts("neuroplasticity").size > 1)
        assertTrue(RsvpWordPartSupport.split(word("neuroplasticity"), config.copy(difficultWordSupport = 0.0), english).size > 1)
        for (limit in listOf(4, 6, 7)) {
            val token = word("electrocardiographies")
            val split = RsvpWordPartSupport.split(token, config.copy(maxChunkLength = limit), english)
            assertTrue(split.all { it.highlightEndExclusive!! - it.highlightStart!! <= limit })
            assertEquals(token.text, split.joinToString("") { it.text.substring(it.highlightStart!!, it.highlightEndExclusive!!) })
        }
    }

    @Test
    fun automaticPartsPreserveSourceMetadataAndFinalPause() {
        val token = word("representatives").copy(pauseAfterMs = 90L, isClauseBoundary = true, isDialogue = true, linkChapterIndex = 3)
        val split = RsvpWordPartSupport.split(token, config, english)
        assertTrue(split.all { it.text == token.text && it.linkChapterIndex == 3 && it.isDialogue })
        assertTrue(split.dropLast(1).all { !it.isClauseBoundary && it.pauseAfterMs == 35L })
        assertTrue(split.last().isClauseBoundary)
        assertEquals(90L, split.last().pauseAfterMs)
    }

    private fun assertFamily(base: String, forms: List<String>) {
        val expected = parts(base)
        for (form in forms) {
            val actual = parts(form)
            assertEquals("$base / $form", expected.size, actual.size)
            if (expected.size > 1) {
                assertEquals(form, ranges(expected).dropLast(1), ranges(actual).dropLast(1))
                assertEquals(form, form.length, actual.last().highlightEndExclusive)
                assertEquals(form, actual.joinToString("") { it.text.substring(it.highlightStart!!, it.highlightEndExclusive!!) })
            }
        }
    }

    private fun word(text: String) = Token(text, TokenType.WORD)
    private fun parts(text: String) = RsvpWordPartSupport.split(word(text), config, english)
    private fun ranges(tokens: List<Token>) = tokens.map { it.highlightStart to it.highlightEndExclusive }
}
