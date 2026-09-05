package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import com.kairo.reader.core.rsvp.segmentation.RsvpAtomKind
import com.kairo.reader.core.rsvp.segmentation.RsvpAtomStream
import com.kairo.reader.core.rsvp.segmentation.RsvpDialogueRole
import com.kairo.reader.core.rsvp.segmentation.RsvpDpSegmenter
import com.kairo.reader.core.rsvp.segmentation.RsvpSegmentationReason
import com.kairo.reader.core.tokenization.TokenizerRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPhaseTwoSegmentationTest {
    private val engine = ComprehensionRsvpEngine()
    private val config =
        RsvpConfig(
            tempoMsPerWord = 180L,
            enablePhraseChunking = true,
            maxWordsPerUnit = 3,
            maxCharsPerUnit = 30,
            maxChunkLength = 40,
            startDelayMs = 0L,
            endDelayMs = 0L,
            rampUpFrames = 0,
            rampDownFrames = 0,
            smoothingAlpha = 1.0,
            maxSpeedupFactor = 1000.0,
            maxSlowdownFactor = 1000.0,
        )

    @Test
    fun namedEntitiesCreateObservableGroupingWithoutOverridingHardLimits() {
        val entity = listOf(word("New"), word("York"), word("City"), word("expanded"))

        val legacy = engine.generateFrames(entity, 0, config, RsvpGenerationOptions.LEGACY)
        val scored = engine.generateFrames(entity, 0, config, englishOptions)

        assertNotEquals(listOf("New", "York", "City"), legacy.first().words())
        assertEquals(listOf("New", "York", "City"), scored.first().words())

        val connector =
            engine.generateFrames(
                listOf(word("Bank"), word("of"), word("England"), word("reported")),
                0,
                config,
                englishOptions,
            )
        assertEquals(listOf("Bank", "of", "England"), connector.first().words())

        val organization =
            engine.generateFrames(
                listOf(word("World"), word("Health"), word("Organization")),
                0,
                config,
                englishOptions,
            )
        assertEquals(listOf("World", "Health", "Organization"), organization.first().words())

        val properAndAcronymFixtures =
            listOf(
                listOf(word("Oxford"), word("University")),
                listOf(word("NASA"), word("Ames")),
            )
        properAndAcronymFixtures.forEach { fixture ->
            val frames = engine.generateFrames(fixture, 0, config, englishOptions)
            assertEquals(fixture.map(Token::text), frames.first().words())
        }

        val capped =
            engine.generateFrames(
                listOf(word("World"), word("Health"), word("Organization")),
                0,
                config.copy(maxCharsPerUnit = 12),
                englishOptions,
            )
        assertFalse(capped.any { frame -> frame.words().size == 3 })

        val falsePositive =
            engine.generateFrames(
                listOf(word("Today"), word("telescope")),
                0,
                config,
                englishOptions,
            )
        assertEquals(listOf("Today"), falsePositive.first().words())
    }

    @Test
    fun dialogueAndSpeakerTagsAffectGroupingOnlyWhenDetectionIsEnabled() {
        val tokens =
            listOf(
                word("Hello", dialogue = true),
                punctuation(",", dialogue = true),
                punctuation("\u201D", dialogue = true),
                word("she"),
                word("whispered"),
            )

        val enabled = engine.generateFrames(tokens, 0, config, englishOptions)
        val disabled =
            engine.generateFrames(
                tokens,
                0,
                config.copy(useDialogueDetection = false),
                englishOptions,
            )

        assertEquals(listOf("she", "whispered"), enabled[1].words())
        assertEquals(listOf("she"), disabled[1].words())

        val invertedTag =
            engine.generateFrames(
                listOf(
                    word("said"),
                    word("Alice"),
                    punctuation(":"),
                    punctuation("\u201C"),
                    word("Go", dialogue = true),
                    word("now", dialogue = true),
                ),
                0,
                config,
                englishOptions,
            )
        assertEquals(listOf("said", "Alice"), invertedTag.first().words())
        assertEquals(listOf("Go", "now"), invertedTag.last().words())
    }

    @Test
    fun scoredFramesNeverCrossPunctuationAndKeepAuthoritativeOrderAndRanges() {
        val tokens =
            listOf(
                punctuation("("),
                word("New"),
                word("York"),
                punctuation(")"),
                word("City"),
                punctuation(","),
                punctuation("$"),
                word("12"),
                punctuation("."),
                word("50"),
            )

        val frames = engine.generateFrames(tokens, 0, config, englishOptions)

        assertEquals(tokens.map(Token::text), frames.flatMap(RsvpFrame::tokens).map(Token::text))
        assertFalse(frames.any { frame -> frame.words().containsAll(listOf("York", "City")) })
        assertFalse(frames.any { frame -> frame.words().containsAll(listOf("12", "50")) })
        assertEquals(listOf("(", "New", "York", ")"), frames.first().tokens.map(Token::text))
        assertTrue(
            frames.zipWithNext().all { (left, right) ->
                left.originalTokenIndex < right.originalTokenIndex ||
                    (left.originalTokenIndex == right.originalTokenIndex && left.resumeCursor < right.resumeCursor)
            }
        )
        assertTrue(
            frames.all { frame ->
                frame.displayOriginalStartIndex < frame.displayOriginalEndExclusive
            }
        )
    }

    @Test
    fun representativeNonEnglishPoliciesPreserveLogicalTokenOrder() {
        val fixtures =
            listOf(
                LanguageFixture("fr", "Le chat dort.", RsvpLanguagePolicy.DEFAULT_NON_ENGLISH),
                LanguageFixture("ja", "私は猫です。", RsvpLanguagePolicy.CJK),
                LanguageFixture("zh", "速度阅读很好。", RsvpLanguagePolicy.CJK),
                LanguageFixture("ko", "한국어 테스트", RsvpLanguagePolicy.CJK),
                LanguageFixture("ar", "هو هنا.", RsvpLanguagePolicy.RTL),
                LanguageFixture("he", "זה טוב.", RsvpLanguagePolicy.RTL),
            )

        fixtures.forEach { fixture ->
            val tokens = tokenize(fixture.languageTag, fixture.text)
            val frames =
                engine.generateFrames(
                    tokens = tokens,
                    startIndex = 0,
                    config = config.copy(maxWordsPerUnit = 2),
                    options = options(fixture.policy),
                )

            assertEquals(
                "Logical order changed for ${fixture.languageTag}",
                tokens.map(Token::text),
                frames.flatMap(RsvpFrame::tokens).map(Token::text),
            )
            assertTrue(
                "Expected a conservative scored pair for ${fixture.languageTag}",
                frames.any { frame -> frame.words().size == 2 },
            )
            assertTrue(
                frames.zipWithNext().all { (left, right) ->
                    left.originalTokenIndex < right.originalTokenIndex ||
                        (left.originalTokenIndex == right.originalTokenIndex && left.resumeCursor < right.resumeCursor)
                }
            )

            val decision =
                RsvpDpSegmenter.selectWordCount(
                    expandedTokens = tokens.expanded(),
                    startCursor = 0,
                    config = config.copy(maxWordsPerUnit = 2),
                    languagePolicy = fixture.policy,
                )
            assertTrue(
                decision.components.none { component ->
                    component.reason in ENGLISH_ONLY_REASONS
                }
            )
        }
    }

    @Test
    fun nativeCjkAndRtlQuotesDriveDialogueRolesAndCompactGrouping() {
        val fixtures =
            listOf(
                LanguageFixture("ja", "「你好世界」", RsvpLanguagePolicy.CJK),
                LanguageFixture("ar", "«هو هنا»", RsvpLanguagePolicy.RTL),
                LanguageFixture("he", "“זה טוב”", RsvpLanguagePolicy.RTL),
            )

        fixtures.forEach { fixture ->
            val tokens = tokenize(fixture.languageTag, fixture.text)
            val atomStream =
                RsvpAtomStream.build(
                    expandedTokens = tokens.expanded(),
                    languagePolicy = fixture.policy,
                    useDialogueDetection = true,
                    useParentheticalAside = false,
                )
            val wordRoles =
                atomStream.atoms
                    .filter { it.kind == RsvpAtomKind.WORD }
                    .map { it.dialogueRole }
            assertTrue(wordRoles.isNotEmpty())
            assertTrue(wordRoles.all { it == RsvpDialogueRole.DIALOGUE_CONTENT })

            val playbackConfig = config.copy(maxWordsPerUnit = 2, useDialogueDetection = true)
            val legacyFrames =
                engine.generateFrames(
                    tokens = tokens,
                    startIndex = 0,
                    config = playbackConfig,
                    options = RsvpGenerationOptions.LEGACY,
                )
            val frames =
                engine.generateFrames(
                    tokens = tokens,
                    startIndex = 0,
                    config = playbackConfig,
                    options = options(fixture.policy),
                )
            // Native quote scanning annotates roles only. Visibility and exact-start punctuation
            // ownership remain the legacy builder's contract (for example, leading « is
            // context-only today).
            assertEquals(
                legacyFrames.map { frame -> frame.tokens.map(Token::text) },
                frames.map { frame -> frame.tokens.map(Token::text) },
            )
            assertEquals(
                legacyFrames.map { frame ->
                    listOf(
                        frame.resumeCursor,
                        frame.nextOriginalTokenIndex,
                        frame.displayOriginalStartIndex,
                        frame.displayOriginalEndExclusive,
                        frame.displayOriginalStartCharacterOffset,
                        frame.displayOriginalEndCharacterOffset,
                    )
                },
                frames.map { frame ->
                    listOf(
                        frame.resumeCursor,
                        frame.nextOriginalTokenIndex,
                        frame.displayOriginalStartIndex,
                        frame.displayOriginalEndExclusive,
                        frame.displayOriginalStartCharacterOffset,
                        frame.displayOriginalEndCharacterOffset,
                    )
                },
            )
            assertTrue(frames.any { it.words().size == 2 })
        }
    }

    @Test
    fun hebrewAcronymsAndCjkBookTitlesAreNotSpeechQuotes() {
        val fixtures =
            listOf(
                LanguageFixture("he", "צה״ל הודיע היום", RsvpLanguagePolicy.RTL),
                LanguageFixture("zh", "《三体》很好", RsvpLanguagePolicy.CJK),
            )

        fixtures.forEach { fixture ->
            val tokens = tokenize(fixture.languageTag, fixture.text)
            val atomStream =
                RsvpAtomStream.build(
                    expandedTokens = tokens.expanded(),
                    languagePolicy = fixture.policy,
                    useDialogueDetection = true,
                    useParentheticalAside = false,
                )
            val wordRoles =
                atomStream.atoms
                    .filter { it.kind == RsvpAtomKind.WORD }
                    .map { it.dialogueRole }

            assertTrue(wordRoles.isNotEmpty())
            assertTrue(wordRoles.none { it == RsvpDialogueRole.DIALOGUE_CONTENT })
        }
    }

    private val englishOptions = options(RsvpLanguagePolicy.ENGLISH)

    private fun options(policy: RsvpLanguagePolicy): RsvpGenerationOptions =
        RsvpGenerationOptions(
            languagePolicy = policy,
            segmentationStrategy = RsvpSegmentationStrategy.SCORED_DP_V2,
        )

    private fun tokenize(
        languageTag: String,
        text: String,
    ): List<Token> =
        TokenizerRegistry.resolve(languageTag).tokenize(
            Chapter(
                index = 0,
                title = "Fixture",
                htmlContent = "",
                plainText = text,
            ),
        )

    private fun List<Token>.expanded(): List<ExpandedToken> =
        mapIndexed { index, token ->
            ExpandedToken(
                token = token,
                originalIndex = index,
                expandedIndex = index,
                sourceCharacterStart = 0,
                sourceCharacterEndExclusive = token.text.length,
            )
        }

    private fun word(
        text: String,
        dialogue: Boolean = false,
    ): Token =
        Token(
            text = text,
            type = TokenType.WORD,
            frequencyScore = 0.69,
            complexityMultiplier = 1.0,
            syllableCount = 1,
            isDialogue = dialogue,
        )

    private fun punctuation(
        text: String,
        dialogue: Boolean = false,
    ): Token = Token(text = text, type = TokenType.PUNCTUATION, isDialogue = dialogue)

    private fun RsvpFrame.words(): List<String> =
        tokens.filter { it.type == TokenType.WORD }.map(Token::text)

    private data class LanguageFixture(val languageTag: String, val text: String, val policy: RsvpLanguagePolicy,)

    private companion object {
        val ENGLISH_ONLY_REASONS =
            setOf(
                RsvpSegmentationReason.TIGHT_PAIR,
                RsvpSegmentationReason.PRONOUN_AUXILIARY,
                RsvpSegmentationReason.AUXILIARY_CONTENT,
                RsvpSegmentationReason.COHERENT_PAIR,
                RsvpSegmentationReason.GENERAL_SHORT_PAIR,
                RsvpSegmentationReason.GLUE_PAIR,
                RsvpSegmentationReason.COMMON_PAIR,
                RsvpSegmentationReason.ENTITY_INTERNAL,
                RsvpSegmentationReason.SPEAKER_TAG,
            )
    }
}
