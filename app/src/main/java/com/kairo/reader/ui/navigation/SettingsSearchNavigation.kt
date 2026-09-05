package com.kairo.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kairo.reader.ui.settings.LocalSettingsSearchTarget
import com.kairo.reader.ui.settings.SettingsSearchPage
import com.kairo.reader.ui.settings.settingsSearchEntries

internal fun NavGraphBuilder.settingsSearchDestination(route: String, content: @Composable () -> Unit) {
    composable(
        "$route?setting={setting}",
        arguments = listOf(
            navArgument("setting") {
                nullable = true
                defaultValue = null
            }
        ),
    ) { entry ->
        val target = settingsSearchEntries.find { it.id == entry.arguments?.getString("setting") }
        CompositionLocalProvider(LocalSettingsSearchTarget provides target) { content() }
    }
}

internal fun SettingsSearchPage.route(): String? = when (this) {
    SettingsSearchPage.RSVP -> KairoRoutes.SETTINGS_RSVP
    SettingsSearchPage.READER -> KairoRoutes.SETTINGS_READER
    SettingsSearchPage.BIONIC -> KairoRoutes.SETTINGS_BIONIC
    SettingsSearchPage.FOCUS -> KairoRoutes.SETTINGS_FOCUS
    SettingsSearchPage.LANGUAGE -> KairoRoutes.SETTINGS_LANGUAGE
    SettingsSearchPage.INFO -> KairoRoutes.SETTINGS_INFO
    else -> null
}
