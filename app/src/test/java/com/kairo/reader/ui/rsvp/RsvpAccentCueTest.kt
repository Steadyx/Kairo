package com.kairo.reader.ui.rsvp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpAccentCueTest {
    @Test
    fun pivotAndWordPartHaveVisibleCuesBeyondColor() {
        val text = buildOrpAnnotatedText(
            fullText = "neuroplasticity",
            pivotPosition = 2,
            pivotColor = Color.Red,
            highlightStart = 5,
            highlightEndExclusive = 10,
            highlightColor = Color.Blue,
        )
        assertEquals(listOf(Color.Blue, Color.Red), text.spanStyles.map { it.item.color })
        assertEquals(listOf(TextDecoration.Underline, TextDecoration.Underline), text.spanStyles.map { it.item.textDecoration })
        assertEquals(listOf(5 to 10, 2 to 3), text.spanStyles.map { it.start to it.end })
    }
}
