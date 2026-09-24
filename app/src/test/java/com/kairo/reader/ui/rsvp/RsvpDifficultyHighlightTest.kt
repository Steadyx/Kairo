package com.kairo.reader.ui.rsvp

import androidx.compose.ui.graphics.Color
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpDifficultyHighlightTest {
    @Test
    fun everyDifficultWordInAPhraseRetainsItsHighlightAndPivot() {
        val tokens = listOf(word("the"), highlighted("quartz"), highlighted("quizzacious"))
        val content = buildOrpTextContent(tokens)
        val plain = buildOrpTextContent(tokens.map { it.copy(highlightStart = null, highlightEndExclusive = null) })
        assertEquals(plain.pivotPosition, content.pivotPosition)
        val annotated = buildOrpAnnotatedText(
            content.fullText,
            content.pivotPosition,
            Color.Red,
            content.highlightStart,
            content.highlightEndExclusive,
            Color.Blue,
            additionalHighlights = content.additionalHighlights,
        )
        assertEquals(
            listOf("quartz", "quizzacious"),
            annotated.spanStyles.filter { it.item.color == Color.Blue }.map {
                annotated.text.substring(it.start, it.end)
            }
        )
        assertEquals(Color.Red, annotated.spanStyles.last().item.color)
    }

    @Test
    fun movingWordPartHighlightRemainsWhenThePivotHighlightIsHidden() {
        val content = buildOrpTextContent(
            listOf(
                highlighted("neuroplasticity").copy(
                    isSubwordChunk = true,
                    highlightStart = 5,
                    highlightEndExclusive = 10,
                )
            )
        )
        val annotated = buildOrpAnnotatedText(
            content.fullText,
            content.pivotPosition,
            Color.Red,
            content.highlightStart,
            content.highlightEndExclusive,
            Color.Blue,
            pivotHighlightVisible = false,
        )
        assertEquals("neuroplasticity", annotated.text)
        assertEquals("plast", annotated.text.substring(annotated.spanStyles.single().start, annotated.spanStyles.single().end))
        assertTrue(annotated.spanStyles.all { it.item.color == Color.Blue })
    }

    private fun word(text: String) = Token(text, TokenType.WORD)
    private fun highlighted(text: String) = word(text).copy(highlightStart = 0, highlightEndExclusive = text.length)
}
