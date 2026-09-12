package com.kairo.reader.data.preferences

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.encode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreferencesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val codec = RsvpProfileJsonCodec()
    private val mapper = UserPreferencesMapper(context, PrefKeys, codec, RsvpConfigPreferenceCodec(PrefKeys, codec))

    @Test
    fun customThemeAndAllFontChoicesLoadFromStoredPreferences() {
        val theme = CustomTheme(background = 0xFF172C29.toInt(), primary = 0xFFEDC892.toInt())
        RsvpFontFamily.entries.forEach { font ->
            val stored = mutablePreferencesOf(
                PrefKeys.customTheme to theme.encode(),
                PrefKeys.readerTheme to ReaderTheme.CUSTOM.name,
                PrefKeys.readerFontFamily to font.name,
                PrefKeys.interfaceFontFamily to font.name,
                PrefKeys.rsvpFontFamily to font.name,
            )
            val loaded = mapper.map(stored)
            assertEquals(theme, loaded.customTheme)
            assertEquals(ReaderTheme.CUSTOM, loaded.readerTheme)
            assertEquals(font, loaded.readerFontFamily)
            assertEquals(font, loaded.interfaceFontFamily)
            assertEquals(font, loaded.rsvpFontFamily)
        }
    }

    @Test
    fun existingAndUnknownPreferencesKeepOriginalFontsAndBuiltInTheme() {
        val defaults = mapper.map(emptyPreferences())
        assertEquals(RsvpFontFamily.MERRIWEATHER, defaults.readerFontFamily)
        assertEquals(RsvpFontFamily.SYSTEM_SANS, defaults.interfaceFontFamily)
        assertEquals(RsvpFontFamily.INTER, defaults.rsvpFontFamily)
        val stored = mutablePreferencesOf(
            PrefKeys.readerTheme to ReaderTheme.NORD.name,
            PrefKeys.readerFontFamily to "REMOVED",
            PrefKeys.interfaceFontFamily to "REMOVED",
            PrefKeys.customTheme to "corrupt",
        )
        val loaded = mapper.map(stored)
        assertEquals(ReaderTheme.NORD, loaded.readerTheme)
        assertEquals(defaults.readerFontFamily, loaded.readerFontFamily)
        assertEquals(defaults.interfaceFontFamily, loaded.interfaceFontFamily)
        assertEquals(CustomTheme(), loaded.customTheme)
    }
}
