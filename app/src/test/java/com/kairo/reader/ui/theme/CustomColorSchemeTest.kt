package com.kairo.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.kairo.reader.core.model.ColorHarmony
import com.kairo.reader.core.model.CustomTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorSchemeTest {
    @Test
    fun generatedAndPinnedPalettesKeepTextAndControlsReadable() {
        val random = Random(42)
        ColorHarmony.entries.forEach { harmony ->
            repeat(250) {
                val theme = CustomTheme(
                    background = random.nextInt(),
                    harmony = harmony,
                    surface = random.nextInt(),
                    text = random.nextInt(),
                    primary = random.nextInt(),
                    secondary = random.nextInt(),
                    tertiary = random.nextInt(),
                )
                assertScheme(theme.materialColorScheme())
                assertScheme(
                    theme.copy(surface = null, text = null, primary = null, secondary = null, tertiary = null).materialColorScheme()
                )
            }
        }
    }

    @Test
    fun backgroundsArePreservedIncludingExtremeAndMidToneColors() {
        listOf(0xFF000000, 0xFFFFFFFF, 0xFF777777, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF807040).forEach { background ->
            val scheme = CustomTheme(background = background.toInt()).materialColorScheme()
            assertEquals(Color(background), scheme.background)
            assertScheme(scheme)
        }
    }

    @Test
    fun changingHarmonyChangesAccentsAndRetainsPinnedRoles() {
        val theme = CustomTheme(background = 0xFFF4EFE4.toInt(), primary = 0xFF203060.toInt())
        val first = theme.materialColorScheme()
        val second = theme.copy(harmony = ColorHarmony.TRIADIC).materialColorScheme()
        assertEquals(first.primary, second.primary)
        assertTrue(first.secondary != second.secondary)
        assertTrue(first.tertiary != second.tertiary)
        assertEquals(theme, theme.copy(harmony = ColorHarmony.TRIADIC).copy(harmony = theme.harmony))
    }

    private fun assertScheme(scheme: ColorScheme) {
        val surfaces = listOf(
            scheme.background, scheme.surface, scheme.surfaceVariant, scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
            scheme.surfaceBright, scheme.surfaceDim
        )
        listOf(
            scheme.onBackground,
            scheme.onSurface,
            scheme.onSurfaceVariant,
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.error
        )
            .forEach { text -> surfaces.forEach { surface -> assertContrast(text, surface) } }
        surfaces.forEach { assertTrue(colorContrast(scheme.outline, it) >= 2.9999f) }
        listOf(
            scheme.onPrimary to scheme.primary, scheme.onSecondary to scheme.secondary, scheme.onTertiary to scheme.tertiary,
            scheme.onPrimaryContainer to scheme.primaryContainer, scheme.onSecondaryContainer to scheme.secondaryContainer,
            scheme.onTertiaryContainer to scheme.tertiaryContainer, scheme.onError to scheme.error,
            scheme.onErrorContainer to scheme.errorContainer, scheme.inverseOnSurface to scheme.inverseSurface,
            scheme.inversePrimary to scheme.inverseSurface, scheme.onPrimaryFixed to scheme.primaryFixed,
            scheme.onSecondaryFixed to scheme.secondaryFixed, scheme.onTertiaryFixed to scheme.tertiaryFixed
        )
            .forEach { (text, surface) -> assertContrast(text, surface) }
    }

    private fun assertContrast(text: Color, surface: Color) {
        assertTrue("Contrast ${colorContrast(text, surface)} for $text over $surface", colorContrast(text, surface) >= 4.4999f)
        assertEquals(1f, text.alpha)
        assertEquals(1f, surface.alpha)
    }
}
