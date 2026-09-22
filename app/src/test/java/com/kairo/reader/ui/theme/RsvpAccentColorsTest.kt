package com.kairo.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpAccentColorsTest {
    @Test
    fun everyBuiltInThemeHasLegibleDistinctRsvpAccents() {
        ReaderTheme.entries.filter { it != ReaderTheme.CUSTOM }.forEach { theme ->
            assertAccents(theme.materialColorScheme())
        }
    }

    @Test
    fun customThemesStayLegibleWhenAccentsMatchOrHavePoorContrast() {
        val random = Random(PALETTE_RANDOM_SEED)
        repeat(CUSTOM_PALETTE_SAMPLES) {
            val accent = random.nextInt()
            assertAccents(
                CustomTheme(
                    background = random.nextInt(),
                    primary = accent,
                    tertiary = if (it % MATCHED_ACCENT_INTERVAL == 0) accent else random.nextInt(),
                ).materialColorScheme()
            )
        }
    }

    private fun assertAccents(scheme: androidx.compose.material3.ColorScheme) {
        val (pivot, wordPart) = scheme.rsvpAccentColors()
        val background = Color(scheme.background.toArgb())
        listOf(pivot, wordPart).forEach { color ->
            assertTrue(colorContrast(Color(color.toArgb()), background) >= WCAG_AA_TEXT_CONTRAST - CONTRAST_TOLERANCE)
        }
        val distance = max(abs(pivot.red - wordPart.red), max(abs(pivot.green - wordPart.green), abs(pivot.blue - wordPart.blue)))
        assertTrue("RSVP accent colors are too similar on $background: $pivot and $wordPart", distance >= MIN_VISIBLE_CHANNEL_DELTA)
    }

    private companion object {
        const val PALETTE_RANDOM_SEED = 417
        const val CUSTOM_PALETTE_SAMPLES = 100
        const val MATCHED_ACCENT_INTERVAL = 2
        const val WCAG_AA_TEXT_CONTRAST = 4.5f
        const val CONTRAST_TOLERANCE = 0.0001f
        const val MIN_VISIBLE_CHANNEL_DELTA = 0.05f
    }
}
