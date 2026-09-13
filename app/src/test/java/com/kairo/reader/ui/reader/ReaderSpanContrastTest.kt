package com.kairo.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.ui.theme.colorContrast
import com.kairo.reader.ui.theme.materialColorScheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSpanContrastTest {
    @Test
    fun yellowHighlightOnMidGreyNoLongerMakesWhiteTextUnreadable() {
        val page = Color(0xFF747474)
        val overlays = listOf(Color(0xFFFFD54F).copy(alpha = 0.18f))
        val background = readerSpanBackground(page, overlays)
        assertTrue(colorContrast(Color.White, background) < 4.5f)
        assertTrue(colorContrast(readerSpanColor(Color.White, page, overlays), background) >= 4.5f)
    }

    @Test
    fun overlappingSavedSearchSelectionAndFocusRemainReadable() {
        val random = Random(673)
        repeat(150) {
            val scheme = CustomTheme(background = random.nextInt(), text = random.nextInt()).materialColorScheme()
            val overlays = listOf(
                Color(0xFFF48FB1).copy(alpha = 0.18f),
                scheme.tertiary.copy(alpha = 0.18f),
                scheme.primary.copy(alpha = 0.22f),
                scheme.primary.copy(alpha = 0.16f)
            )
            for (count in 1..overlays.size) {
                val active = overlays.take(count)
                val background = readerSpanBackground(scheme.background, active)
                listOf(scheme.onBackground, scheme.tertiary, scheme.primary.copy(alpha = 0.95f)).forEach { text ->
                    val result = readerSpanColor(text, scheme.background, active)
                    assertTrue(colorContrast(Color(result.toArgb()), Color(background.toArgb())) >= 4.5f)
                    assertEquals(1f, result.alpha)
                }
            }
        }
    }
}
