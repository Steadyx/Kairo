package com.kairo.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class RsvpAccentColors(val pivot: Color, val wordPart: Color)

/** Keep the two RSVP cues legible and distinct while following editable theme accents. */
internal fun ColorScheme.rsvpAccentColors(): RsvpAccentColors {
    val pivot = contrastingColor(primary, listOf(background))
    val themedWordPart = contrastingColor(tertiary, listOf(background))
    val wordPart = if (channelDistance(pivot, themedWordPart) >= MIN_ACCENT_DISTANCE) {
        themedWordPart
    } else {
        val hue = colorHue(pivot)
        val lightness = if (colorContrast(Color.White, background) > colorContrast(Color.Black, background)) {
            DARK_BACKGROUND_WORD_PART_LIGHTNESS
        } else {
            LIGHT_BACKGROUND_WORD_PART_LIGHTNESS
        }
        val derived = DISTINCT_HUE_OFFSETS.map { offset ->
            contrastingColor(
                Color.hsl((hue + offset) % FULL_CIRCLE_DEGREES, WORD_PART_SATURATION, lightness),
                listOf(background),
            )
        }
        val best = derived.maxBy { channelDistance(pivot, it) }
        if (channelDistance(pivot, best) >= MIN_ACCENT_DISTANCE) {
            best
        } else {
            val extremes = listOf(Color.Black, Color.White).filter { colorContrast(it, background) >= MIN_TEXT_CONTRAST }
            (derived + extremes).maxBy { channelDistance(pivot, it) }
        }
    }
    return RsvpAccentColors(pivot, wordPart)
}

private fun channelDistance(first: Color, second: Color): Float =
    max(abs(first.red - second.red), max(abs(first.green - second.green), abs(first.blue - second.blue)))

private fun colorHue(color: Color): Float {
    val high = max(color.red, max(color.green, color.blue))
    val low = min(color.red, min(color.green, color.blue))
    val delta = high - low
    if (delta < NEUTRAL_CHROMA_THRESHOLD) return NEUTRAL_HUE_DEGREES
    val sector = when (high) {
        color.red -> (color.green - color.blue) / delta
        color.green -> (color.blue - color.red) / delta + GREEN_HUE_SECTOR
        else -> (color.red - color.green) / delta + BLUE_HUE_SECTOR
    }
    return (sector * HUE_SECTOR_DEGREES + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
}

private const val MIN_ACCENT_DISTANCE = 0.16f
private const val MIN_TEXT_CONTRAST = 4.5f
private const val WORD_PART_SATURATION = 0.9f
private const val LIGHT_BACKGROUND_WORD_PART_LIGHTNESS = 0.38f
private const val DARK_BACKGROUND_WORD_PART_LIGHTNESS = 0.72f
private const val NEUTRAL_CHROMA_THRESHOLD = 0.02f
private const val NEUTRAL_HUE_DEGREES = 40f
private const val GREEN_HUE_SECTOR = 2f
private const val BLUE_HUE_SECTOR = 4f
private const val HUE_SECTOR_DEGREES = 60f
private const val FULL_CIRCLE_DEGREES = 360f
private const val HUE_QUARTER_TURN = 90f
private const val HUE_WARM_TURN = 150f
private const val HUE_COOL_TURN = 210f
private const val HUE_THREE_QUARTER_TURN = 270f
private val DISTINCT_HUE_OFFSETS = listOf(HUE_QUARTER_TURN, HUE_WARM_TURN, HUE_COOL_TURN, HUE_THREE_QUARTER_TURN)
