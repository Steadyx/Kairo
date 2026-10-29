package com.kairo.reader.ui.focus

import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.navigation.KairoRoutes

internal fun shouldApplyFocusMode(
    route: String?,
    preferences: UserPreferences,
): Boolean =
    preferences.focusModeEnabled &&
        when (if (route?.startsWith("settings/") == true) route.substringBefore('?') else route) {
            KairoRoutes.SETTINGS,
            KairoRoutes.SETTINGS_LANGUAGE,
            KairoRoutes.SETTINGS_INFO,
            KairoRoutes.SETTINGS_CHANGELOG,
            KairoRoutes.SETTINGS_RSVP,
            KairoRoutes.SETTINGS_BIONIC,
            KairoRoutes.SETTINGS_THEME,
            KairoRoutes.SETTINGS_READER,
            KairoRoutes.SETTINGS_FOCUS,
            -> true
            KairoRoutes.READER,
            KairoRoutes.READER_WITH_POSITION,
            -> preferences.focusApplyInReader
            KairoRoutes.RSVP,
            KairoRoutes.BIONIC,
            -> preferences.focusApplyInRsvp
            else -> false
        }
