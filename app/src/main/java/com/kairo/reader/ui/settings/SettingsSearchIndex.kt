package com.kairo.reader.ui.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import com.kairo.reader.R
import java.text.Normalizer
import java.util.Locale

// IDs are stable route arguments; titles and descriptions come from the same resources as the controls.
data class SettingsSearchEntry(
    val id: String,
    val page: SettingsSearchPage,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val sectionRes: Int? = null,
    val advanced: Boolean = false,
    @StringRes val requiresRes: Int? = null,
    val aliases: List<Int> = emptyList(),
)

enum class SettingsSearchPage(@StringRes val titleRes: Int) {
    RSVP(R.string.rsvp_settings_title),
    READER(R.string.reader_settings_title),
    BIONIC(R.string.bionic_settings_title),
    FOCUS(R.string.focus_settings_title),
    LANGUAGE(R.string.settings_language_title),
    INFO(R.string.info_settings_title),
    UPDATES(R.string.update_check_title),
    TUTORIAL(R.string.settings_starting_tutorial_title),
    RESET(R.string.settings_title),
}

internal val settingsSearchEntries = rsvpSearchEntries + readerSearchEntries + bionicSearchEntries +
    focusSearchEntries + infoSearchEntries + languageSearchEntries + updatesSearchEntries + tutorialSearchEntries + resetSearchEntries

internal data class SettingsSearchDocument(
    val entry: SettingsSearchEntry,
    val title: String,
    val description: String,
    val location: String,
    val requirement: String?,
    val keywords: String,
) {
    val normalizedTitle = normalizeSettingsQuery(title)
    val searchableText = normalizeSettingsQuery(listOfNotNull(title, description, location, requirement, keywords).joinToString(" "))
}

internal fun buildSettingsSearchIndex(resources: Resources): List<SettingsSearchDocument> =
    settingsSearchEntries.map { entry ->
        val location = listOfNotNull(
            resources.getString(entry.page.titleRes),
            R.string.settings_advanced_title.takeIf { entry.advanced }?.let(resources::getString),
            entry.sectionRes?.let(resources::getString),
        ).joinToString(" › ")
        SettingsSearchDocument(
            entry = entry,
            title = resources.getString(entry.titleRes),
            description = resources.getString(entry.descriptionRes),
            location = location,
            requirement = entry.requiresRes?.let { resources.getString(R.string.settings_search_requires, resources.getString(it)) },
            keywords = entry.aliases.joinToString(" ") { resources.getString(it) },
        )
    }

internal fun searchSettings(index: List<SettingsSearchDocument>, query: String): List<SettingsSearchDocument> {
    val normalized = normalizeSettingsQuery(query)
    if (normalized.isEmpty()) return emptyList()
    val terms = normalized.split(' ')
    return index.filter { document -> terms.all { it in document.searchableText } }
        .sortedWith(
            compareByDescending<SettingsSearchDocument> { document ->
                when {
                    document.normalizedTitle == normalized -> EXACT_TITLE_SCORE
                    document.normalizedTitle.startsWith(normalized) -> PREFIX_TITLE_SCORE
                    terms.all { it in document.normalizedTitle } -> ALL_TITLE_TERMS_SCORE
                    else -> terms.count { it in document.normalizedTitle }
                }
            }.thenBy { it.title }.thenBy { it.entry.id }
        )
}

internal fun normalizeSettingsQuery(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(SEARCH_SEPARATORS, " ")
        .trim()

private val COMBINING_MARKS = Regex("\\p{M}+")
private val SEARCH_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
private const val EXACT_TITLE_SCORE = 100
private const val PREFIX_TITLE_SCORE = 80
private const val ALL_TITLE_TERMS_SCORE = 60
