@file:Suppress("MatchingDeclarationName")

package com.kairo.reader.ui.navigation

import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.kairo.reader.KairoApplication
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.settings.BionicSettingsScreen
import com.kairo.reader.ui.settings.FocusSettingsScreen
import com.kairo.reader.ui.settings.InfoSettingsScreen
import com.kairo.reader.ui.settings.LanguageSettingsScreen
import com.kairo.reader.ui.settings.ReaderSettingsScreen
import com.kairo.reader.ui.settings.RsvpSettingsScreen
import com.kairo.reader.ui.settings.SettingsHomeActions
import com.kairo.reader.ui.settings.SettingsHomeScreen
import com.kairo.reader.ui.settings.SettingsTutorialActions
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import kotlinx.coroutines.launch

internal data class SettingsRouteDependencies(
    val container: KairoApplication,
    val navController: NavHostController,
    val prefs: UserPreferences,
    val tutorialState: StartingTutorialOverlayState?,
    val onCheckForUpdates: () -> Unit,
    val onOpenStartingTutorial: () -> Unit,
    val onTutorialNext: () -> Unit,
    val onTutorialPrevious: () -> Unit,
    val onTutorialSkip: () -> Unit,
)

// Navigation graph declarations are intentionally kept together so route ownership remains auditable.
@Suppress("LongMethod")
internal fun NavGraphBuilder.settingsRoutes(dependencies: SettingsRouteDependencies) {
    composable(KairoRoutes.SETTINGS) {
        val coroutineScope = rememberCoroutineScope()
        SettingsHomeScreen(
            actions =
            SettingsHomeActions(
                onOpenLanguage = {
                    dependencies.navController.navigate(KairoRoutes.SETTINGS_LANGUAGE)
                },
                onOpenRsvp = { dependencies.navController.navigate(KairoRoutes.SETTINGS_RSVP) },
                onOpenBionic = { dependencies.navController.navigate(KairoRoutes.SETTINGS_BIONIC) },
                onOpenReader = { dependencies.navController.navigate(KairoRoutes.SETTINGS_READER) },
                onOpenFocus = { dependencies.navController.navigate(KairoRoutes.SETTINGS_FOCUS) },
                onOpenInfo = { dependencies.navController.navigate(KairoRoutes.SETTINGS_INFO) },
                onCheckForUpdates = dependencies.onCheckForUpdates,
                onOpenStartingTutorial = dependencies.onOpenStartingTutorial,
                onReset = {
                    coroutineScope.launch {
                        dependencies.container.preferencesRepository.reset()
                    }
                },
                onOpenSearchResult = { entry ->
                    val route = entry.page.route()
                    if (route != null) {
                        dependencies.navController.navigate("$route?setting=${entry.id}")
                    } else if (entry.page == com.kairo.reader.ui.settings.SettingsSearchPage.UPDATES) {
                        dependencies.onCheckForUpdates()
                    } else if (entry.page == com.kairo.reader.ui.settings.SettingsSearchPage.TUTORIAL) {
                        dependencies.onOpenStartingTutorial()
                    }
                },
                onClose = { dependencies.navController.popBackStack() },
            ),
            tutorialState = dependencies.tutorialState,
            tutorialActions =
            SettingsTutorialActions(
                onNext = dependencies.onTutorialNext,
                onPrevious = dependencies.onTutorialPrevious,
                onSkip = dependencies.onTutorialSkip,
            ),
        )
    }

    settingsSearchDestination(KairoRoutes.SETTINGS_LANGUAGE) {
        LanguageSettingsScreen(onBack = { dependencies.navController.popBackStack() })
    }

    settingsSearchDestination(KairoRoutes.SETTINGS_INFO) {
        InfoSettingsScreen(onBack = { dependencies.navController.popBackStack() })
    }

    settingsSearchDestination(KairoRoutes.SETTINGS_RSVP) {
        val coroutineScope = rememberCoroutineScope()
        RsvpSettingsScreen(
            preferences = dependencies.prefs,
            onSelectRsvpProfile = { profileId ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.selectRsvpProfile(profileId)
                }
            },
            onSaveRsvpProfile = { name, config ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.saveRsvpCustomProfile(name, config)
                }
            },
            onDeleteRsvpProfile = { profileId ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.deleteRsvpCustomProfile(profileId)
                }
            },
            onRsvpTempoMsPerWordChange = { tempoMsPerWord ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpTempoMsPerWord(
                        tempoMsPerWord
                    )
                }
            },
            onRsvpConfigChange = { updater ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpConfig(updater)
                }
            },
            onUnlockExtremeSpeedChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateUnlockExtremeSpeed(enabled)
                }
            },
            onRsvpFontSizeChange = { size ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpFontSize(size)
                }
            },
            onRsvpTextBrightnessChange = { brightness ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpTextBrightness(
                        brightness
                    )
                }
            },
            onRsvpFontWeightChange = { weight ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpFontWeight(weight)
                }
            },
            onRsvpFontFamilyChange = { family ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpFontFamily(family)
                }
            },
            onRsvpVerticalBiasChange = { bias ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpVerticalBias(bias)
                }
            },
            onRsvpHorizontalBiasChange = { bias ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateRsvpHorizontalBias(bias)
                }
            },
            onBack = { dependencies.navController.popBackStack() },
        )
    }

    settingsSearchDestination(KairoRoutes.SETTINGS_READER) {
        val coroutineScope = rememberCoroutineScope()
        ReaderSettingsScreen(
            preferences = dependencies.prefs,
            onFontSizeChange = { size ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateFontSize(size)
                }
            },
            onThemeChange = { theme ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateTheme(theme.name)
                }
            },
            onTextBrightnessChange = { brightness ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateReaderTextBrightness(
                        brightness
                    )
                }
            },
            onInvertedScrollChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateInvertedScroll(enabled)
                }
            },
            onBack = { dependencies.navController.popBackStack() },
        )
    }

    settingsSearchDestination(KairoRoutes.SETTINGS_BIONIC) {
        val coroutineScope = rememberCoroutineScope()
        BionicSettingsScreen(
            preferences = dependencies.prefs.bionicReading,
            onFixationStrengthChange = { strength ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository
                        .updateBionicFixationStrength(strength)
                }
            },
            onHighlightStrengthChange = { strength ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository
                        .updateBionicHighlightStrength(strength)
                }
            },
            onFontSizeChange = { size ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateBionicFontSize(size)
                }
            },
            onTextBrightnessChange = { brightness ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository
                        .updateBionicTextBrightness(brightness)
                }
            },
            onBack = { dependencies.navController.popBackStack() },
        )
    }

    settingsSearchDestination(KairoRoutes.SETTINGS_FOCUS) {
        val coroutineScope = rememberCoroutineScope()
        FocusSettingsScreen(
            preferences = dependencies.prefs,
            onFocusModeEnabledChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateFocusModeEnabled(enabled)
                }
            },
            onFocusHideStatusBarChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateFocusHideStatusBar(enabled)
                }
            },
            onFocusPauseNotificationsChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateFocusPauseNotifications(
                        enabled
                    )
                }
            },
            onFocusApplyInReaderChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateFocusApplyInReader(enabled)
                }
            },
            onFocusApplyInRsvpChange = { enabled ->
                coroutineScope.launch {
                    dependencies.container.preferencesRepository.updateFocusApplyInRsvp(enabled)
                }
            },
            onBack = { dependencies.navController.popBackStack() },
        )
    }
}
