@file:Suppress("MagicNumber")

package com.kairo.reader.core.model

/** Null roles follow the background and harmony; explicit roles remain pinned when either changes. */
data class CustomTheme(
    val background: Int = 0xFFF4EFE4.toInt(),
    val harmony: ColorHarmony = ColorHarmony.ANALOGOUS,
    val surface: Int? = null,
    val text: Int? = null,
    val primary: Int? = null,
    val secondary: Int? = null,
    val tertiary: Int? = null,
)

enum class ColorHarmony { TONAL, ANALOGOUS, COMPLEMENTARY, TRIADIC }

fun parseThemeColor(value: String): Int? =
    value.trim().removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }
        ?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())

fun themeColorHex(value: Int): String = "#" + (value.toLong() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** Versioned, delimiter-safe encoding shared by persistence and the saveable editor draft. */
fun CustomTheme.encode(): String =
    listOf("1", themeColorHex(background), harmony.name, surface, text, primary, secondary, tertiary)
        .joinToString("|") { if (it is Int) themeColorHex(it) else it?.toString().orEmpty() }

fun decodeCustomTheme(value: String?): CustomTheme {
    val parts = value?.split('|') ?: return CustomTheme()
    if (parts.size != THEME_FIELD_COUNT || parts[0] != "1") return CustomTheme()
    val defaults = CustomTheme()
    return CustomTheme(
        background = parseThemeColor(parts[1]) ?: defaults.background,
        harmony = ColorHarmony.entries.find { it.name == parts[2] } ?: defaults.harmony,
        surface = parseThemeColor(parts[3]),
        text = parseThemeColor(parts[4]),
        primary = parseThemeColor(parts[5]),
        secondary = parseThemeColor(parts[6]),
        tertiary = parseThemeColor(parts[7]),
    )
}

private const val THEME_FIELD_COUNT = 8
