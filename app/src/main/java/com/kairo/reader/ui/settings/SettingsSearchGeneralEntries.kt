package com.kairo.reader.ui.settings

import com.kairo.reader.R

internal val readerSearchEntries = listOf(
    SettingsSearchEntry(
        id = "reader.reader_font_size_title",
        page = SettingsSearchPage.READER,
        titleRes = R.string.reader_font_size_title,
        descriptionRes = R.string.settings_search_font_size_description,
    ),
    SettingsSearchEntry(
        id = "reader.reader_theme_title",
        page = SettingsSearchPage.READER,
        titleRes = R.string.reader_theme_title,
        descriptionRes = R.string.settings_search_theme_description,
        aliases = listOf(
            R.string.reader_theme_light,
            R.string.reader_theme_dark,
            R.string.reader_theme_sepia,
            R.string.reader_theme_ink,
            R.string.reader_theme_linen,
            R.string.reader_theme_mist,
            R.string.reader_theme_sage,
            R.string.reader_theme_plum,
            R.string.reader_theme_ember,
            R.string.reader_theme_nord,
            R.string.reader_theme_forest,
            R.string.reader_theme_cyberpunk,
        ),
    ),
    SettingsSearchEntry(
        id = "reader.reader_text_brightness_title",
        page = SettingsSearchPage.READER,
        titleRes = R.string.reader_text_brightness_title,
        descriptionRes = R.string.reader_text_brightness_subtitle,
    ),
    SettingsSearchEntry(
        id = "reader.reader_invert_swipe_title",
        page = SettingsSearchPage.READER,
        titleRes = R.string.reader_invert_swipe_title,
        descriptionRes = R.string.reader_invert_swipe_subtitle,
    ),
)

internal val bionicSearchEntries = listOf(
    SettingsSearchEntry(
        id = "bionic.bionic_text_size_title",
        page = SettingsSearchPage.BIONIC,
        titleRes = R.string.bionic_text_size_title,
        descriptionRes = R.string.bionic_text_size_subtitle,
        aliases = listOf(R.string.settings_search_font_size_description),
    ),
    SettingsSearchEntry(
        id = "bionic.bionic_fixation_title",
        page = SettingsSearchPage.BIONIC,
        titleRes = R.string.bionic_fixation_title,
        descriptionRes = R.string.bionic_fixation_subtitle,
        advanced = true,
    ),
    SettingsSearchEntry(
        id = "bionic.bionic_highlight_title",
        page = SettingsSearchPage.BIONIC,
        titleRes = R.string.bionic_highlight_title,
        descriptionRes = R.string.bionic_highlight_subtitle,
        advanced = true,
    ),
    SettingsSearchEntry(
        id = "bionic.bionic_brightness_title",
        page = SettingsSearchPage.BIONIC,
        titleRes = R.string.bionic_brightness_title,
        descriptionRes = R.string.bionic_brightness_subtitle,
        advanced = true,
    ),
)

internal val focusSearchEntries = listOf(
    SettingsSearchEntry(
        id = "focus.focus_enable_title",
        page = SettingsSearchPage.FOCUS,
        titleRes = R.string.focus_enable_title,
        descriptionRes = R.string.focus_mode_subtitle,
    ),
    SettingsSearchEntry(
        id = "focus.focus_hide_status_bar_title",
        page = SettingsSearchPage.FOCUS,
        titleRes = R.string.focus_hide_status_bar_title,
        descriptionRes = R.string.focus_hide_status_bar_subtitle,
        requiresRes = R.string.focus_enable_title,
    ),
    SettingsSearchEntry(
        id = "focus.focus_pause_notifications_title",
        page = SettingsSearchPage.FOCUS,
        titleRes = R.string.focus_pause_notifications_title,
        descriptionRes = R.string.focus_pause_notifications_subtitle,
        requiresRes = R.string.focus_enable_title,
    ),
    SettingsSearchEntry(
        id = "focus.focus_apply_reader_title",
        page = SettingsSearchPage.FOCUS,
        titleRes = R.string.focus_apply_reader_title,
        descriptionRes = R.string.focus_apply_reader_subtitle,
        requiresRes = R.string.focus_enable_title,
    ),
    SettingsSearchEntry(
        id = "focus.focus_apply_rsvp_title",
        page = SettingsSearchPage.FOCUS,
        titleRes = R.string.focus_apply_rsvp_title,
        descriptionRes = R.string.focus_apply_rsvp_subtitle,
        requiresRes = R.string.focus_enable_title,
    ),
)

internal val infoSearchEntries = listOf(
    SettingsSearchEntry(
        id = "info.info_website_title",
        page = SettingsSearchPage.INFO,
        titleRes = R.string.info_website_title,
        descriptionRes = R.string.info_website_subtitle,
    ),
    SettingsSearchEntry(
        id = "info.info_contribute_title",
        page = SettingsSearchPage.INFO,
        titleRes = R.string.info_contribute_title,
        descriptionRes = R.string.info_contribute_subtitle,
    ),
    SettingsSearchEntry(
        id = "info.info_contact_title",
        page = SettingsSearchPage.INFO,
        titleRes = R.string.info_contact_title,
        descriptionRes = R.string.info_contact_subtitle,
    ),
)

internal val languageSearchEntries = listOf(
    SettingsSearchEntry(
        id = "language.settings_language_title",
        page = SettingsSearchPage.LANGUAGE,
        titleRes = R.string.settings_language_title,
        descriptionRes = R.string.settings_search_language_description,
    ),
)

internal val updatesSearchEntries = listOf(
    SettingsSearchEntry(
        id = "updates.update_check_title",
        page = SettingsSearchPage.UPDATES,
        titleRes = R.string.update_check_title,
        descriptionRes = R.string.update_check_subtitle,
    ),
)

internal val tutorialSearchEntries = listOf(
    SettingsSearchEntry(
        id = "tutorial.settings_starting_tutorial_title",
        page = SettingsSearchPage.TUTORIAL,
        titleRes = R.string.settings_starting_tutorial_title,
        descriptionRes = R.string.settings_starting_tutorial_subtitle,
    ),
)

internal val resetSearchEntries = listOf(
    SettingsSearchEntry(
        id = "reset.settings_reset_defaults",
        page = SettingsSearchPage.RESET,
        titleRes = R.string.settings_reset_defaults,
        descriptionRes = R.string.settings_reset_confirm_message,
    ),
)
