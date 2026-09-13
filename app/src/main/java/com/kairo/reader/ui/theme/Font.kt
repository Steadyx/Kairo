@file:Suppress("MagicNumber")
@file:OptIn(ExperimentalTextApi::class)

package com.kairo.reader.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kairo.reader.R

// Merriweather - serif font for Reader mode
val MerriweatherFontFamily =
    FontFamily(
        Font(
            R.font.merriweather,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Light.weight),
            ),
        ),
        Font(
            R.font.merriweather,
            weight = FontWeight.Normal,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Normal.weight),
            ),
        ),
        Font(
            R.font.merriweather,
            weight = FontWeight.Medium,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Medium.weight),
            ),
        ),
        Font(
            R.font.merriweather,
            weight = FontWeight.SemiBold,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.SemiBold.weight),
            ),
        ),
        Font(
            R.font.merriweather,
            weight = FontWeight.Bold,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Bold.weight),
            ),
        ),
    )

// Inter - sans-serif font option for RSVP mode
val InterFontFamily =
    FontFamily(
        Font(
            R.font.inter,
            weight = FontWeight.Light,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Light.weight),
            ),
        ),
        Font(
            R.font.inter,
            weight = FontWeight.Normal,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Normal.weight),
            ),
        ),
        Font(
            R.font.inter,
            weight = FontWeight.Medium,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Medium.weight),
            ),
        ),
        Font(
            R.font.inter,
            weight = FontWeight.SemiBold,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.SemiBold.weight),
            ),
        ),
        Font(
            R.font.inter,
            weight = FontWeight.Bold,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Bold.weight),
            ),
        ),
    )

// Roboto - sans-serif font option for RSVP mode
val RobotoFontFamily =
    FontFamily(
        Font(
            R.font.roboto,
            weight = FontWeight.Light,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Light.weight),
            ),
        ),
        Font(
            R.font.roboto,
            weight = FontWeight.Normal,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Normal.weight),
            ),
        ),
        Font(
            R.font.roboto,
            weight = FontWeight.Medium,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Medium.weight),
            ),
        ),
        Font(
            R.font.roboto,
            weight = FontWeight.SemiBold,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.SemiBold.weight),
            ),
        ),
        Font(
            R.font.roboto,
            weight = FontWeight.Bold,
            variationSettings =
            FontVariation.Settings(
                FontVariation.weight(FontWeight.Bold.weight),
            ),
        ),
    )

internal val LoraFontFamily = variableFontFamily(R.font.lora, 400, 700)
internal val LexendFontFamily = variableFontFamily(R.font.lexend, 100, 900)

private fun variableFontFamily(resource: Int, minimumWeight: Int, maximumWeight: Int): FontFamily =
    FontFamily(
        (100..900 step 100).map { weight ->
            Font(
                resource,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight.coerceIn(minimumWeight, maximumWeight))
                )
            )
        }
    )

internal fun com.kairo.reader.core.model.RsvpFontFamily.composeFontFamily(): FontFamily = when (this) {
    com.kairo.reader.core.model.RsvpFontFamily.INTER -> InterFontFamily
    com.kairo.reader.core.model.RsvpFontFamily.ROBOTO -> RobotoFontFamily
    com.kairo.reader.core.model.RsvpFontFamily.MERRIWEATHER -> MerriweatherFontFamily
    com.kairo.reader.core.model.RsvpFontFamily.LORA -> LoraFontFamily
    com.kairo.reader.core.model.RsvpFontFamily.LEXEND -> LexendFontFamily
    com.kairo.reader.core.model.RsvpFontFamily.SYSTEM_SANS -> FontFamily.SansSerif
    com.kairo.reader.core.model.RsvpFontFamily.SYSTEM_SERIF -> FontFamily.Serif
    com.kairo.reader.core.model.RsvpFontFamily.MONOSPACE -> FontFamily.Monospace
}
