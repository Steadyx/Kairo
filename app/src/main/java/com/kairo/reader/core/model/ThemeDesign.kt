@file:Suppress("MagicNumber")

package com.kairo.reader.core.model

/** A reusable appearance, independent of reading position, pacing, and text size. */
data class ThemeDesign(
    val theme: ReaderTheme = ReaderTheme.CUSTOM,
    val custom: CustomTheme = CustomTheme(),
    val readerFont: RsvpFontFamily = RsvpFontFamily.MERRIWEATHER,
    val interfaceFont: RsvpFontFamily = RsvpFontFamily.SYSTEM_SANS,
    val timedFont: RsvpFontFamily = RsvpFontFamily.INTER,
) {
    fun encode(): String = listOf(theme.name, readerFont.name, interfaceFont.name, timedFont.name, custom.encode()).joinToString("~")
}

fun UserPreferences.themeDesign(): ThemeDesign =
    ThemeDesign(readerTheme, customTheme, readerFontFamily, interfaceFontFamily, rsvpFontFamily)

fun decodeThemeDesign(value: String): ThemeDesign? {
    val parts = value.split('~', limit = DESIGN_FIELD_COUNT)
    if (parts.size != DESIGN_FIELD_COUNT) return null
    return ThemeDesign(
        theme = ReaderTheme.entries.find { it.name == parts[0] } ?: return null,
        readerFont = RsvpFontFamily.entries.find { it.name == parts[1] } ?: return null,
        interfaceFont = RsvpFontFamily.entries.find { it.name == parts[2] } ?: return null,
        timedFont = RsvpFontFamily.entries.find { it.name == parts[3] } ?: return null,
        custom = decodeCustomTheme(parts[4]),
    )
}

data class SavedTheme(val id: String, val name: String, val design: ThemeDesign)

const val MAX_SAVED_THEMES = 24
const val MAX_THEME_NAME_LENGTH = 60
private const val DESIGN_FIELD_COUNT = 5
