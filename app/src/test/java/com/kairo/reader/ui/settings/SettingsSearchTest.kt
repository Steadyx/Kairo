package com.kairo.reader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test
    fun exactTitleRanksAbovePrefixAndDescription() {
        val index = listOf(document("Other", "Theme controls"), document("Theme options"), document("Theme"))
        assertEquals(listOf("Theme", "Theme options", "Other"), searchSettings(index, "theme").map { it.title })
    }

    @Test
    fun allWordsMustMatchAcrossTitleDescriptionAndLocation() {
        val index = listOf(document("Brightness", "Text intensity", "RSVP"), document("Brightness", "Text intensity", "Reader"))
        assertEquals(listOf(index.first()), searchSettings(index, "RSVP text brightness"))
        assertTrue(searchSettings(index, "RSVP missing").isEmpty())
    }

    @Test
    fun matchesOptionsAndDependencies() {
        val entry = document("Theme", keywords = "Sepia Nord")
        assertEquals(listOf(entry), searchSettings(listOf(entry), "nord"))
        val dependent = document("Guide brightness", requirement = "Available when alignment guide is enabled")
        assertEquals(listOf(dependent), searchSettings(listOf(dependent), "alignment brightness"))
    }

    @Test
    fun ignoresCaseAccentsAndPunctuationWithoutDroppingNonLatinText() {
        assertEquals("eclairage 文字", normalizeSettingsQuery(" ÉCLAIRAGE—文字! "))
        val index = listOf(document("Éclairage"), document("文字"))
        assertEquals("Éclairage", searchSettings(index, "eclairage").single().title)
        assertEquals("文字", searchSettings(index, "文字").single().title)
    }

    @Test
    fun emptyAndPunctuationOnlyQueriesReturnNoResults() {
        val index = listOf(document("Theme"))
        listOf("", "  ", "?!").forEach { assertTrue(searchSettings(index, it).isEmpty()) }
    }

    @Test
    fun catalogHasUniqueStableIdsAndCoversEveryPage() {
        assertEquals(settingsSearchEntries.size, settingsSearchEntries.map { it.id }.distinct().size)
        assertEquals(SettingsSearchPage.entries.toSet(), settingsSearchEntries.map { it.page }.toSet())
        assertTrue(settingsSearchEntries.all { it.id.matches(Regex("[a-z0-9_.]+")) })
    }

    private fun document(
        title: String,
        description: String = "",
        location: String = "",
        keywords: String = "",
        requirement: String? = null,
    ) = SettingsSearchDocument(
        SettingsSearchEntry(title, SettingsSearchPage.READER, 0, 0),
        title,
        description,
        location,
        requirement,
        keywords,
    )
}
