@file:Suppress("MagicNumber")

package com.kairo.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.kairo.reader.core.model.ColorHarmony
import com.kairo.reader.core.model.CustomTheme
import kotlin.math.max
import kotlin.math.min

internal fun colorContrast(first: Color, second: Color): Float =
    (max(first.luminance(), second.luminance()) + 0.05f) / (min(first.luminance(), second.luminance()) + 0.05f)

/** Move only as far toward black or white as needed, retaining the requested hue where possible. */
internal fun contrastingColor(requested: Color, backgrounds: List<Color>, minimum: Float = 4.5f): Color {
    val opaque = Color(requested.copy(alpha = 1f).toArgb())
    val checks = backgrounds + backgrounds.map { Color(it.toArgb()) }
    fun passes(color: Color) = checks.all { colorContrast(color, it) >= minimum }
    if (passes(opaque)) return opaque
    val end = listOf(Color.Black, Color.White).maxBy { candidate -> checks.minOf { colorContrast(candidate, it) } }
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2f
        if (passes(Color(lerp(opaque, end, middle).toArgb()))) high = middle else low = middle
    }
    return Color(lerp(opaque, end, high).toArgb())
}

private fun foreground(background: Color): Color =
    if (colorContrast(Color.Black, background) >= colorContrast(Color.White, background)) Color.Black else Color.White

/** All surfaces stay on the same side of the text contrast boundary, even with a pinned surface. */
@Suppress("LongMethod")
internal fun CustomTheme.materialColorScheme(): ColorScheme {
    val bg = Color(background).copy(alpha = 1f)
    val ink = foreground(bg)
    val dark = ink == Color.White
    val end = if (dark) Color.Black else Color.White
    val baseSurface = contrastingColor(surface?.let(::Color) ?: lerp(bg, end, 0.12f), listOf(ink))
    val containers = listOf(0f, 0.12f, 0.24f, 0.36f, 0.48f).map { lerp(baseSurface, end, it) }
    val surfaces = containers + bg
    val onSurface = contrastingColor(text?.let(::Color) ?: ink, surfaces)
    val hue = backgroundHue(bg)
    val offsets = when (harmony) {
        ColorHarmony.TONAL -> listOf(0f, 0f, 0f)
        ColorHarmony.ANALOGOUS -> listOf(0f, 30f, 330f)
        ColorHarmony.COMPLEMENTARY -> listOf(180f, 0f, 150f)
        ColorHarmony.TRIADIC -> listOf(0f, 120f, 240f)
    }
    val accents = listOf(primary, secondary, tertiary).mapIndexed { index, override ->
        val seed = override?.let(::Color) ?: Color.hsl((hue + offsets[index]) % 360f, 0.48f - index * 0.08f, 0.5f)
        contrastingColor(seed, surfaces)
    }
    val primaryContainer = lerp(bg, accents[0], 0.18f)
    val secondaryContainer = lerp(bg, accents[1], 0.14f)
    val tertiaryContainer = lerp(bg, accents[2], 0.14f)
    val error = contrastingColor(Color(0xFFBA1A1A), surfaces)
    val errorContainer = lerp(bg, error, 0.14f)
    val inverse = if (dark) Color(0xFFF1EEE8) else Color(0xFF262521)
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        background = bg, onBackground = onSurface,
        surface = baseSurface, onSurface = onSurface,
        surfaceVariant = containers[2], onSurfaceVariant = onSurface,
        surfaceContainerLowest = containers[0], surfaceContainerLow = containers[1],
        surfaceContainer = containers[2], surfaceContainerHigh = containers[3], surfaceContainerHighest = containers[4],
        surfaceBright = if (dark) baseSurface else containers[4], surfaceDim = if (dark) containers[4] else baseSurface,
        primary = accents[0], onPrimary = foreground(accents[0]),
        primaryContainer = primaryContainer, onPrimaryContainer = foreground(primaryContainer),
        secondary = accents[1], onSecondary = foreground(accents[1]),
        secondaryContainer = secondaryContainer, onSecondaryContainer = foreground(secondaryContainer),
        tertiary = accents[2], onTertiary = foreground(accents[2]),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = foreground(tertiaryContainer),
        outline = contrastingColor(lerp(onSurface, bg, 0.4f), surfaces, 3f),
        outlineVariant = lerp(onSurface, baseSurface, 0.8f),
        inverseSurface = inverse, inverseOnSurface = foreground(inverse),
        inversePrimary = contrastingColor(accents[0], listOf(inverse)),
        error = error, onError = foreground(error), errorContainer = errorContainer, onErrorContainer = foreground(errorContainer),
        primaryFixed = primaryContainer, primaryFixedDim = primaryContainer,
        onPrimaryFixed = foreground(primaryContainer), onPrimaryFixedVariant = foreground(primaryContainer),
        secondaryFixed = secondaryContainer, secondaryFixedDim = secondaryContainer,
        onSecondaryFixed = foreground(secondaryContainer), onSecondaryFixedVariant = foreground(secondaryContainer),
        tertiaryFixed = tertiaryContainer, tertiaryFixedDim = tertiaryContainer,
        onTertiaryFixed = foreground(tertiaryContainer), onTertiaryFixedVariant = foreground(tertiaryContainer),
        surfaceTint = Color.Transparent, scrim = Color.Black,
    )
}

/** Neutral backgrounds get a stable warm hue instead of an arbitrary red cast. */
private fun backgroundHue(color: Color): Float {
    val high = max(color.red, max(color.green, color.blue))
    val low = min(color.red, min(color.green, color.blue))
    val delta = high - low
    if (delta < 0.02f) return 40f
    val sector = when (high) {
        color.red -> (color.green - color.blue) / delta
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
    }
    return (sector * 60f + 360f) % 360f
}
