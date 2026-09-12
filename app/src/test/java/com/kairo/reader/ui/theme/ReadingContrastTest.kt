package com.kairo.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kairo.reader.core.model.ColorHarmony
import com.kairo.reader.core.model.CustomTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingContrastTest {
    @Test
    fun contrastMatchesReferenceRatios() {
        assertEquals(21f, colorContrast(Color.Black, Color.White), 0.0001f)
        assertEquals(1f, colorContrast(Color.Gray, Color.Gray), 0.0001f)
    }

    @Test
    fun readingBrightnessCannotReduceCustomTextBelowAAAfterRgbConversion() {
        val random = Random(87)
        repeat(150) {
            val scheme = CustomTheme(background = random.nextInt(), text = random.nextInt()).materialColorScheme()
            listOf(0.55f, 0.7f, 0.88f, 1f).forEach { brightness ->
                val text = contrastSafeReadingColor(scheme.onBackground.copy(alpha = brightness), scheme.background)
                assertTrue(colorContrast(Color(text.toArgb()), Color(scheme.background.toArgb())) >= 4.5f)
            }
        }
    }

    @Test
    fun generatedAccentsAndReadingRolesMeetThresholdsInEightBitSrgb() {
        val random = Random(214)
        ColorHarmony.entries.forEach { harmony ->
            repeat(150) {
                val scheme = CustomTheme(background = random.nextInt(), harmony = harmony, surface = random.nextInt()).materialColorScheme()
                val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainer, scheme.surfaceContainerHighest)
                surfaces.forEach { background ->
                    listOf(scheme.onBackground, scheme.primary, scheme.secondary, scheme.tertiary).forEach { text ->
                        assertTrue(colorContrast(Color(text.toArgb()), Color(background.toArgb())) >= 4.5f)
                    }
                    assertTrue(colorContrast(Color(scheme.outline.toArgb()), Color(background.toArgb())) >= 3f)
                }
            }
        }
    }
}
