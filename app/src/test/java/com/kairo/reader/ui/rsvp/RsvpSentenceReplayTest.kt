package com.kairo.reader.ui.rsvp

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.tokenization.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpSentenceReplayTest {
    @Test
    fun inlineEllipsesKeepTheWholeSentenceIncludingQuotedHesitations() {
        for (text in listOf(
            "I thought… perhaps I was wrong.",
            "I thought... perhaps I was wrong.",
            "She whispered, “Wait… please stay.”",
            "He said “maybe…” and then waited."
        )) {
            val tokens = tokenize("Earlier sentence. $text")
            val start = tokens.indexOfFirst { it.text == text.substringBefore(' ') }
            assertEquals(text, start, findReplaySentenceStartTokenIndex(tokens, tokens.indexOfLast { it.type == TokenType.WORD }))
        }
    }

    @Test
    fun sentenceEndingEllipsesStillSeparateSentencesAndParagraphs() {
        for ((text, firstWord) in listOf(
            "I waited… Then Sarah arrived." to "Then",
            "I waited… “Sarah arrived.”" to "Sarah",
            "I waited…\n\nperhaps tomorrow." to "perhaps",
        )) {
            val tokens = tokenize(text)
            assertEquals(
                text,
                tokens.indexOfFirst { it.text == firstWord },
                findReplaySentenceStartTokenIndex(tokens, tokens.indexOfLast { it.type == TokenType.WORD }),
            )
        }
    }

    @Test
    fun replayRestoresTheWholeSentenceAcrossClausesAndAbbreviations() {
        val tokens = tokenize("We left. Dr. Smith arrived, and the meeting began.")
        val current = tokens.indexOfFirst { it.text == "began" }
        assertEquals(tokens.indexOfFirst { it.text == "Dr" || it.text == "Dr." }, findReplaySentenceStartTokenIndex(tokens, current))
    }

    @Test
    fun replayDuringAParagraphPauseReturnsToTheSentenceJustRead() {
        val tokens = tokenize("Earlier sentence. We stayed.\n\nThey left.")
        val pause = tokens.indexOfFirst { it.type == TokenType.PARAGRAPH_BREAK }
        assertEquals(tokens.indexOfFirst { it.text == "We" }, findReplaySentenceStartTokenIndex(tokens, pause))
    }

    @Test
    fun decimalsDoNotEndTheSentenceAndParagraphsDo() {
        val tokens = tokenize("Earlier paragraph\n\nThe price was 3.14 pounds today.")
        assertEquals(tokens.indexOfFirst { it.text == "The" }, findReplaySentenceStartTokenIndex(tokens, tokens.lastIndex - 1))
    }

    @Test
    fun sentenceStartDoesNotUnexpectedlyReplayThePreviousSentence() {
        val tokens = tokenize("One ends. Another begins.")
        val start = tokens.indexOfFirst { it.text == "Another" }
        assertEquals(start, findReplaySentenceStartTokenIndex(tokens, start))
        assertEquals(0, findReplaySentenceStartTokenIndex(emptyList(), 100))
        val first = tokens.indexOfFirst { it.type == TokenType.WORD }
        assertEquals(first, findReplaySentenceStartTokenIndex(tokens, -1))
    }

    private fun tokenize(text: String) = Tokenizer().tokenize(Chapter(0, null, "", text))
}
