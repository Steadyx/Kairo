package com.kairo.reader.data.preferences

import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.MAX_SAVED_THEMES
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.core.model.ThemeDesign
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeLibraryCodecTest {
    @Test
    fun namesColoursAndAllThreeFontsRoundTripWithoutDelimiterLoss() {
        val design = ThemeDesign(
            custom = CustomTheme(sourcePreset = ReaderTheme.EMBER),
            readerFont = RsvpFontFamily.LEXEND,
            interfaceFont = RsvpFontFamily.LORA,
            timedFont = RsvpFontFamily.MONOSPACE
        )
        val themes =
            listOf(SavedTheme("one", "夜 | ~ \"Reading\"", design), SavedTheme("two", "Linen", ThemeDesign(theme = ReaderTheme.LINEN)))
        assertEquals(themes, ThemeLibraryCodec.decode(ThemeLibraryCodec.encode(themes)))
    }

    @Test
    fun damagedAndDuplicateRowsDoNotEraseValidDesigns() {
        val item = JSONObject().put("id", "one").put("name", "Valid").put("design", ThemeDesign().encode())
        val raw = JSONObject().put("version", 1).put(
            "themes",
            JSONArray().put("broken").put(item).put(item)
                .put(JSONObject().put("id", "bad").put("name", "Broken").put("design", "bad"))
        ).toString()
        assertEquals(listOf(SavedTheme("one", "Valid", ThemeDesign())), ThemeLibraryCodec.decode(raw))
        listOf(null, "", "{", "{\"version\":2,\"themes\":[]}").forEach { assertTrue(ThemeLibraryCodec.decode(it).isEmpty()) }
    }

    @Test
    fun collectionSizeIsBounded() {
        val themes = (0..MAX_SAVED_THEMES).map { SavedTheme("$it", "Theme $it", ThemeDesign()) }
        assertEquals(MAX_SAVED_THEMES, ThemeLibraryCodec.decode(ThemeLibraryCodec.encode(themes)).size)
    }
}
