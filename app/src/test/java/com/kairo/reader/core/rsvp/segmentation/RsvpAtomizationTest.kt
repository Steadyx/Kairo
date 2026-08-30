package com.kairo.reader.core.rsvp.segmentation

import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.rsvp.RsvpLanguagePolicy
import com.kairo.reader.core.rsvp.engine.ExpandedToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpAtomizationTest {
    @Test
    fun atomizationPreservesEveryExpandedCursorAndSourceRange() {
        val tokens =
            listOf(
                punctuation("\u201C"),
                word("Price"),
                punctuation(":"),
                punctuation("$"),
                word("12"),
                punctuation("."),
                word("50"),
                punctuation("("),
                word("net"),
                punctuation(")"),
            )
        val expanded =
            tokens.mapIndexed { index, token ->
                ExpandedToken(
                    token = token,
                    originalIndex = index / 2,
                    expandedIndex = index,
                    sourceCharacterStart = index * 3,
                    sourceCharacterEndExclusive = (index * 3) + token.text.length,
                )
            }

        val atoms =
            RsvpAtomStream.build(
                expandedTokens = expanded,
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                useDialogueDetection = true,
                useParentheticalAside = true,
            ).atoms

        assertEquals(expanded.size, atoms.size)
        expanded.zip(atoms).forEach { (source, atom) ->
            assertEquals(source.expandedIndex, atom.expandedCursor)
            assertEquals(source.originalIndex, atom.originalIndex)
            assertEquals(source.sourceCharacterStart, atom.sourceCharacterStart)
            assertEquals(source.sourceCharacterEndExclusive, atom.sourceCharacterEndExclusive)
            assertEquals(source.token, atom.token)
        }
        assertEquals(
            tokens.map { token ->
                if (token.type == TokenType.WORD) RsvpAtomKind.WORD else RsvpAtomKind.PUNCTUATION
            },
            atoms.map(RsvpAtom::kind),
        )
    }

    @Test
    fun englishEntitySpansAreBoundedAndSuppressLoneSentenceInitialCaps() {
        val atoms =
            atomize(
                listOf(
                    word("Today"),
                    word("arrived"),
                    punctuation("."),
                    word("World"),
                    word("Health"),
                    word("Organization"),
                    word("report"),
                    punctuation("."),
                    word("Bank"),
                    word("of"),
                    word("England"),
                ),
            )

        assertNull(atoms[0].entitySpanId)
        assertNull(atoms[1].entitySpanId)
        assertTrue(atoms.subList(3, 6).mapNotNull(RsvpAtom::entitySpanId).distinct().size == 1)
        assertTrue(atoms.subList(8, 11).mapNotNull(RsvpAtom::entitySpanId).distinct().size == 1)
        assertNull(atoms[6].entitySpanId)
    }

    @Test
    fun speakerTagsUsePureEnglishHeuristicsOnlyNearDialogue() {
        val tokens =
            listOf(
                word("Hello", dialogue = true),
                punctuation(",", dialogue = true),
                punctuation("\u201D", dialogue = true),
                word("she"),
                word("whispered"),
                punctuation("."),
                word("Later"),
                word("she"),
                word("whispered"),
            )

        val roles = atomize(tokens).map(RsvpAtom::dialogueRole)

        assertEquals(RsvpDialogueRole.DIALOGUE_CONTENT, roles[0])
        assertEquals(RsvpDialogueRole.SPEAKER_TAG, roles[3])
        assertEquals(RsvpDialogueRole.SPEAKER_TAG, roles[4])
        assertEquals(RsvpDialogueRole.NARRATION, roles[7])
        assertEquals(RsvpDialogueRole.NARRATION, roles[8])
    }

    @Test
    fun speakerLikeWordsInsideQuotesRemainDialogueContent() {
        val roles =
            atomize(
                tokens =
                listOf(
                    punctuation("\u201C"),
                    word("She"),
                    word("said"),
                    word("nothing"),
                    punctuation("\u201D"),
                ),
                languagePolicy = RsvpLanguagePolicy.ENGLISH,
                useDialogueDetection = true,
                useParentheticalAside = false,
            ).filter { it.kind == RsvpAtomKind.WORD }
                .map(RsvpAtom::dialogueRole)

        assertEquals(List(3) { RsvpDialogueRole.DIALOGUE_CONTENT }, roles)
    }

    @Test
    fun disabledDialogueDetectionLeavesAllRolesNeutral() {
        val roles =
            atomize(
                tokens = listOf(word("Hello", dialogue = true), word("she"), word("said")),
                useDialogueDetection = false,
            ).map(RsvpAtom::dialogueRole)

        assertEquals(List(roles.size) { RsvpDialogueRole.NARRATION }, roles)
    }

    @Test
    fun dialogueAndParentheticalFlagsRemainIndependentInAllCombinations() {
        val tokens =
            listOf(
                punctuation("("),
                word("aside"),
                punctuation(")"),
                word("speech", dialogue = true),
            )
        val expected =
            mapOf(
                (false to false) to
                    listOf(RsvpDialogueRole.NARRATION, RsvpDialogueRole.NARRATION),
                (true to false) to
                    listOf(RsvpDialogueRole.NARRATION, RsvpDialogueRole.DIALOGUE_CONTENT),
                (false to true) to
                    listOf(RsvpDialogueRole.PARENTHETICAL_ASIDE, RsvpDialogueRole.NARRATION),
                (true to true) to
                    listOf(
                        RsvpDialogueRole.PARENTHETICAL_ASIDE,
                        RsvpDialogueRole.DIALOGUE_CONTENT,
                    ),
            )

        expected.forEach { (flags, expectedRoles) ->
            val roles =
                atomize(
                    tokens = tokens,
                    useDialogueDetection = flags.first,
                    useParentheticalAside = flags.second,
                ).filter { it.kind == RsvpAtomKind.WORD }
                    .map(RsvpAtom::dialogueRole)
            assertEquals(expectedRoles, roles)
        }
    }

    @Test
    fun disabledParentheticalAnalysisCannotInfluenceScoredGrouping() {
        val tokens = listOf(punctuation("("), word("zog"), word("mif"), punctuation(")"))
        val baseConfig =
            RsvpConfig(
                enablePhraseChunking = true,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 12,
                useDialogueDetection = false,
            )

        val enabled =
            RsvpDpSegmenter.selectWordCount(
                expandedTokens = tokens.expanded(),
                startCursor = 0,
                config = baseConfig.copy(useParentheticalAside = true),
            )
        val disabled =
            RsvpDpSegmenter.selectWordCount(
                expandedTokens = tokens.expanded(),
                startCursor = 0,
                config = baseConfig.copy(useParentheticalAside = false),
            )

        assertEquals(2, enabled.selectedWordCount)
        assertTrue(enabled.components.any { it.reason == RsvpSegmentationReason.PARENTHETICAL_COHESION })
        assertEquals(1, disabled.selectedWordCount)
        assertTrue(disabled.components.none { it.reason == RsvpSegmentationReason.PARENTHETICAL_COHESION })
    }

    @Test
    fun contextualBoundaryStrengthKeepsAbbreviationsAndDecimalsNeutral() {
        assertEquals(
            RHYTHM_BOUNDARY_NONE,
            boundaryStrength(listOf(word("Dr"), punctuation("."), word("Smith"))),
        )
        assertEquals(
            RHYTHM_BOUNDARY_NONE,
            boundaryStrength(listOf(word("12"), punctuation("."), word("50"))),
        )
    }

    @Test
    fun contextualBoundaryStrengthCoversEllipsisAndLatinClausePunctuation() {
        assertEquals(
            RHYTHM_BOUNDARY_HARD,
            boundaryStrength(listOf(word("Wait"), punctuation("\u2026"), word("Now"))),
        )
        assertEquals(
            RHYTHM_BOUNDARY_COMMA,
            boundaryStrength(listOf(word("walked"), punctuation(","), word("home"))),
        )
        assertEquals(
            RHYTHM_BOUNDARY_CLAUSE,
            boundaryStrength(listOf(word("walked"), punctuation(","), word("but"))),
        )
        assertEquals(
            RHYTHM_BOUNDARY_COMMA,
            boundaryStrength(
                tokens = listOf(word("walked"), punctuation(","), word("but")),
                languagePolicy = RsvpLanguagePolicy.DEFAULT_NON_ENGLISH,
            ),
        )
    }

    @Test
    fun contextualBoundaryStrengthCoversArabicAndCjkPunctuationSets() {
        listOf('\u061F', '\u06D4', '\u3002', '\uFF01', '\uFF1F').forEach { character ->
            assertEquals(
                RHYTHM_BOUNDARY_HARD,
                boundaryStrength(listOf(word("first"), punctuation(character.toString()), word("next"))),
            )
        }
        listOf('\u060C', '\u3001', '\uFF0C').forEach { character ->
            assertEquals(
                RHYTHM_BOUNDARY_COMMA,
                boundaryStrength(listOf(word("first"), punctuation(character.toString()), word("next"))),
            )
        }
        assertEquals(
            RHYTHM_BOUNDARY_CLAUSE,
            boundaryStrength(listOf(word("first"), punctuation("\u061B"), word("next"))),
        )
    }

    @Test
    fun nativePairedQuotesCreateRequestLocalDialogueRoles() {
        val fixtures =
            listOf(
                Triple(RsvpLanguagePolicy.CJK, "\u300C", "\u300D"),
                Triple(RsvpLanguagePolicy.RTL, "\u00AB", "\u00BB"),
                Triple(RsvpLanguagePolicy.RTL, "\u201C", "\u201D"),
            )

        fixtures.forEach { (policy, opening, closing) ->
            val atoms =
                atomize(
                    tokens =
                    listOf(
                        punctuation(opening),
                        word("one"),
                        word("two"),
                        punctuation(closing),
                        word("outside"),
                    ),
                    languagePolicy = policy,
                    useDialogueDetection = true,
                    useParentheticalAside = false,
                )
            val wordRoles = atoms.filter { it.kind == RsvpAtomKind.WORD }.map(RsvpAtom::dialogueRole)
            assertEquals(
                listOf(
                    RsvpDialogueRole.DIALOGUE_CONTENT,
                    RsvpDialogueRole.DIALOGUE_CONTENT,
                    RsvpDialogueRole.NARRATION,
                ),
                wordRoles,
            )
        }
    }

    @Test
    fun lexicalSpeakerTagsRemainEnglishOnly() {
        val tokens =
            listOf(
                punctuation("\u00AB", dialogue = true),
                word("hello", dialogue = true),
                punctuation("\u00BB", dialogue = true),
                word("she"),
                word("said"),
            )

        val roles =
            atomize(
                tokens = tokens,
                languagePolicy = RsvpLanguagePolicy.RTL,
                useDialogueDetection = true,
                useParentheticalAside = false,
            ).filter { it.kind == RsvpAtomKind.WORD }
                .map(RsvpAtom::dialogueRole)

        assertEquals(
            listOf(
                RsvpDialogueRole.DIALOGUE_CONTENT,
                RsvpDialogueRole.NARRATION,
                RsvpDialogueRole.NARRATION,
            ),
            roles,
        )
    }

    private fun atomize(
        tokens: List<Token>,
        languagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.ENGLISH,
        useDialogueDetection: Boolean = true,
        useParentheticalAside: Boolean = true,
    ): List<RsvpAtom> =
        RsvpAtomStream.build(
            expandedTokens = tokens.expanded(),
            languagePolicy = languagePolicy,
            useDialogueDetection = useDialogueDetection,
            useParentheticalAside = useParentheticalAside,
        ).atoms

    private fun boundaryStrength(
        tokens: List<Token>,
        languagePolicy: RsvpLanguagePolicy = RsvpLanguagePolicy.ENGLISH,
    ): Int {
        val stream =
            RsvpAtomStream.build(
                expandedTokens = tokens.expanded(),
                languagePolicy = languagePolicy,
                useDialogueDetection = false,
                useParentheticalAside = false,
            )
        val finalWordCursor = stream.atoms.indexOfLast { it.kind == RsvpAtomKind.WORD }
        return stream.boundaryStrengthBefore(finalWordCursor)
    }

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
            isDialogue = dialogue,
        )

    private fun punctuation(
        text: String,
        dialogue: Boolean = false,
    ): Token = Token(text = text, type = TokenType.PUNCTUATION, isDialogue = dialogue)
}
