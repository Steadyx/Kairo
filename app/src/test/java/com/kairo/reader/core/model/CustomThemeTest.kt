package com.kairo.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomThemeTest {
    @Test
    fun pinnedAndAutomaticRolesRoundTrip() {
        val theme = CustomTheme(
            background = 0xFF000000.toInt(),
            harmony = ColorHarmony.COMPLEMENTARY,
            surface = 0xFF123456.toInt(),
            text = 0xFFFEDCBA.toInt(),
            tertiary = 0xFF789ABC.toInt()
        )
        assertEquals(theme, decodeCustomTheme(theme.encode()))
        assertEquals(CustomTheme(), decodeCustomTheme(CustomTheme().encode()))
    }

    @Test
    fun unsupportedOrMalformedDataFallsBackWithoutLosingValidRoles() {
        listOf(null, "", "2|#ffffff|TONAL|||||", "1|broken").forEach { assertEquals(CustomTheme(), decodeCustomTheme(it)) }
        val recovered = decodeCustomTheme("1|#001122|UNKNOWN|invalid||#445566||")
        assertEquals(0xFF001122.toInt(), recovered.background)
        assertEquals(ColorHarmony.ANALOGOUS, recovered.harmony)
        assertNull(recovered.surface)
        assertEquals(0xFF445566.toInt(), recovered.primary)
    }

    @Test
    fun hexInputAcceptsSixDigitsOnlyAndIsAlwaysOpaque() {
        assertEquals(0xFFAABBCC.toInt(), parseThemeColor(" #aabbcc "))
        assertEquals(0xFF000000.toInt(), parseThemeColor("000000"))
        listOf("#abc", "#12345678", "12345g", "-12345", "##123456", "").forEach { assertNull(parseThemeColor(it)) }
        assertEquals("#AABBCC", themeColorHex(0xFFAABBCC.toInt()))
    }
}
