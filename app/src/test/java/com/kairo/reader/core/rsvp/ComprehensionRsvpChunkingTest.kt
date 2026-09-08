package com.kairo.reader.core.rsvp

import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensionRsvpChunkingTest : ComprehensionRsvpTestBase() {
    @Test
    fun currencySymbolPunctuationStaysWithFollowingNumber() {
        val tokens = listOf(w("extra"), p("\$"), w("20"))

        val frames = engine.generateFrames(tokens, 0, stableConfig)
        val resumedAtNumber = engine.generateFrames(tokens, 2, stableConfig)

        assertEquals(listOf("extra"), frames[0].tokens.map { it.text })
        assertEquals(listOf("\$", "20"), frames[1].tokens.map { it.text })
        assertEquals(listOf("\$", "20"), resumedAtNumber.first().tokens.map { it.text })
    }

    @Test
    fun hyphenatedWordsAddMicroPauseBetweenParts() {
        val config =
            stableConfig.copy(
                rarityExtraMaxMs = 0L,
                syllableExtraMs = 0L,
                complexityStrength = 0.0,
                lengthStrength = 0.0,
            )

        val frames = engine.generateFrames(
            tokens = listOf(w("self-aware")),
            startIndex = 0,
            config = config
        )

        assertEquals(2, frames.size)
        val expectedFirst = config.tempoMsPerWord + (config.tempoMsPerWord / 4)
        assertTrue(
            "Expected first hyphen-part frame around ${expectedFirst}ms, got ${frames[0].durationMs}ms",
            frames[0].durationMs in (expectedFirst - 2)..(expectedFirst + 2),
        )
    }

    @Test
    fun longWordsSplitIntoChunks() {
        val config = stableConfig.copy(maxChunkLength = 6, enablePhraseChunking = false)
        val word = "antidisestablishment"
        val expectedChunks = (word.length + config.maxChunkLength - 1) / config.maxChunkLength

        val frames = engine.generateFrames(
            tokens = listOf(w(word)),
            startIndex = 0,
            config = config
        )

        assertEquals(expectedChunks, frames.size)
        val chunkTokens = frames.map { frame ->
            frame.tokens.first { it.type == TokenType.WORD }
        }
        chunkTokens.forEach { token ->
            assertEquals(word, token.text)
            assertTrue("Expected highlight range on chunk", token.highlightStart != null)
            assertTrue("Expected highlight range on chunk", token.highlightEndExclusive != null)
        }
        val ranges =
            chunkTokens
                .map { token -> token.highlightStart!! to token.highlightEndExclusive!! }
                .sortedBy { it.first }
        assertEquals(0, ranges.first().first)
        assertEquals(word.length, ranges.last().second)
        ranges.forEach { (start, end) ->
            assertTrue(
                "Expected chunk length <= ${config.maxChunkLength}",
                (end - start) <= config.maxChunkLength,
            )
        }
        ranges.drop(1).forEachIndexed { index, range ->
            assertEquals(
                "Expected contiguous ranges",
                ranges[index].second,
                range.first,
            )
        }
        chunkTokens.dropLast(1).forEach { token ->
            assertEquals(config.subwordChunkPauseMs, token.pauseAfterMs)
        }
        assertEquals(0L, chunkTokens.last().pauseAfterMs)
    }

    @Test
    fun phraseChunkingRespectsMaxWordsPerUnitOne() {
        val config =
            stableConfig.copy(
                enablePhraseChunking = true,
                maxWordsPerUnit = 1,
                maxCharsPerUnit = 24,
            )
        val tokens = listOf(w("in"), w("the"), w("house"))

        val frames = engine.generateFrames(tokens, 0, config)

        val firstWords = frames.first().tokens.filter { it.type == TokenType.WORD }.map { it.text }
        assertEquals(listOf("in"), firstWords)
    }

    @Test
    fun phraseChunkingCanBuildThreeWordUnitsWithinBudget() {
        val config =
            stableConfig.copy(
                enablePhraseChunking = true,
                maxWordsPerUnit = 3,
                maxCharsPerUnit = 24,
            )
        val tokens = listOf(w("in"), w("the"), w("house"), w("today"))

        val frames = engine.generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
        val firstWords = frames.first().tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("in", "the", "house"), firstWords)
    }

    @Test
    fun phraseChunkingBuildsPronounAuxiliaryTriplets() {
        val config =
            stableConfig.copy(
                enablePhraseChunking = true,
                maxWordsPerUnit = 3,
                maxCharsPerUnit = 18,
            )
        val tokens = listOf(w("I"), w("was"), w("reading"), w("slowly"))

        val frames = engine.generateFrames(tokens, 0, config, RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH))
        val firstWords =
            frames.first().tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("I", "was", "reading"), firstWords)
    }

    @Test
    fun phraseChunkingKeepsSemanticAnchorWordsSeparateUnlessHinted() {
        val config =
            stableConfig.copy(
                enablePhraseChunking = true,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 14,
            )

        val unhinted = engine.generateFrames(
            listOf(w("go"), w("not"), w("there")),
            0,
            config,
            RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        )
        val hinted = engine.generateFrames(
            listOf(w("not"), w("yet"), w("ready")),
            0,
            config,
            RsvpGenerationOptions(RsvpLanguagePolicy.ENGLISH)
        )

        assertEquals(
            listOf("go"),
            unhinted.first().tokens.filter { it.type == TokenType.WORD }.map { it.text },
        )
        assertEquals(
            listOf("not", "yet"),
            hinted.first().tokens.filter { it.type == TokenType.WORD }.map { it.text },
        )
    }

    @Test
    fun chunkCharBudgetCountsWordCharsNotLeadingPunctuation() {
        val config =
            stableConfig.copy(
                enablePhraseChunking = true,
                maxWordsPerUnit = 2,
                maxCharsPerUnit = 5,
            )
        val tokens = listOf(p("("), w("in"), w("the"), p(")"), w("house"))

        val frames = engine.generateFrames(tokens, 0, config)
        val firstWords = frames.first().tokens.filter { it.type == TokenType.WORD }.map { it.text }

        assertEquals(listOf("in", "the"), firstWords)
    }

    @Test
    fun signedNumericTokensAreNotSplit() {
        val tokens = listOf(w("-35c"))
        val frames = engine.generateFrames(tokens, 0, stableConfig)

        assertEquals(1, frames.size)
        val word =
            frames
                .first()
                .tokens
                .first { it.type == TokenType.WORD }
                .text
        assertEquals("-35c", word)
    }
}
