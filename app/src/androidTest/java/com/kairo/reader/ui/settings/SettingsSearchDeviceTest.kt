package com.kairo.reader.ui.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.navigation.settingsSearchDestination
import com.kairo.reader.ui.reader.ReaderMenuActions
import com.kairo.reader.ui.reader.ReaderMenuOverlay
import com.kairo.reader.ui.reader.ReaderMenuState
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSearchDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun catalogResolvesAllDescriptionsAndOptions() {
        val index = buildSettingsSearchIndex(composeRule.activity.resources)
        assertEquals(settingsSearchEntries.size, index.size)
        assertTrue(index.all { it.title.isNotBlank() && it.description.isNotBlank() && it.location.isNotBlank() })
        assertTrue(searchSettings(index, "Nord").any { it.entry.page == SettingsSearchPage.READER })
    }

    @Test
    fun noResultsAndClearRestoreCategories() {
        composeRule.setContent { KairoTheme { SettingsHomeScreen(actions()) } }
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextInput("zznonexistingsettingzz")
        composeRule.onNodeWithText(text(R.string.settings_search_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.settings_search_clear)).performClick()
        composeRule.onNodeWithText(text(R.string.rsvp_settings_title)).assertIsDisplayed()
    }

    @Test
    fun resultOpensAdvancedControlAndBackRestoresQuery() {
        val target = settingsSearchEntries.first { it.titleRes == R.string.rsvp_vertical_position_title }
        composeRule.setContent {
            KairoTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        SettingsHomeScreen(actions().copy(onOpenSearchResult = { nav.navigate("rsvp?setting=${it.id}") }))
                    }
                    settingsSearchDestination("rsvp") {
                        RsvpSettingsScreen(
                            preferences = UserPreferences(),
                            onSelectRsvpProfile = {}, onSaveRsvpProfile = { _, _ -> }, onDeleteRsvpProfile = {},
                            onRsvpTempoMsPerWordChange = {}, onRsvpConfigChange = {}, onUnlockExtremeSpeedChange = {},
                            onRsvpFontSizeChange = {}, onRsvpTextBrightnessChange = {}, onRsvpFontWeightChange = {},
                            onRsvpFontFamilyChange = {}, onRsvpVerticalBiasChange = {}, onRsvpHorizontalBiasChange = {},
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextInput(text(target.titleRes))
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performImeAction()
        composeRule.onNodeWithText(text(target.descriptionRes)).assertIsDisplayed()
        capture("search-result")
        composeRule.onNodeWithText(text(target.descriptionRes)).performClick()
        composeRule.onNodeWithText(text(target.titleRes)).assertIsDisplayed()
        capture("advanced-target")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(target.descriptionRes)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextReplacement("Nord")
        composeRule.onNodeWithText(text(R.string.reader_theme_title)).assertIsDisplayed()
    }

    @Test
    fun resetSearchResultRequiresConfirmation() {
        var resets = 0
        composeRule.setContent { KairoTheme { SettingsHomeScreen(actions().copy(onReset = { resets++ })) } }
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextInput("reset")
        composeRule.onNodeWithText(text(R.string.settings_reset_confirm_message)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_reset_confirm_title)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, resets) }
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(0, resets) }
    }

    @Test
    fun readerPageSearchShowsOnlyReaderSettingsAndRevealsControl() {
        composeRule.setContent {
            KairoTheme {
                ReaderSettingsScreen(
                    preferences = UserPreferences(),
                    onFontSizeChange = {},
                    onThemeChange = {},
                    onTextBrightnessChange = {},
                    onInvertedScrollChange = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextInput("brightness")
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performImeAction()
        composeRule.onNodeWithText(text(R.string.reader_text_brightness_subtitle)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(text(R.string.reader_text_brightness_title)).assertIsDisplayed()
        capture("reader-page-target")
    }

    @Test
    fun rsvpPageSearchRevealsAdvancedControlWithoutLeavingPage() {
        composeRule.setContent {
            KairoTheme {
                RsvpSettingsScreen(
                    preferences = UserPreferences(),
                    onSelectRsvpProfile = {}, onSaveRsvpProfile = { _, _ -> }, onDeleteRsvpProfile = {},
                    onRsvpTempoMsPerWordChange = {}, onRsvpConfigChange = {}, onUnlockExtremeSpeedChange = {},
                    onRsvpFontSizeChange = {}, onRsvpTextBrightnessChange = {}, onRsvpFontWeightChange = {},
                    onRsvpFontFamilyChange = {}, onRsvpVerticalBiasChange = {}, onRsvpHorizontalBiasChange = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextInput("vertical")
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performImeAction()
        composeRule.onNodeWithText(text(R.string.settings_search_vertical_description)).performClick()
        composeRule.onNodeWithText(text(R.string.rsvp_vertical_position_title)).assertIsDisplayed()
        capture("rsvp-page-target")
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(text(R.string.settings_search_vertical_description)).assertIsDisplayed()
    }

    @Test
    fun inReadingReaderSettingsAlsoSupportSearch() {
        val prefs = UserPreferences()
        composeRule.setContent {
            KairoTheme {
                ReaderMenuOverlay(
                    state = ReaderMenuState(
                        fontSizeSp = prefs.readerFontSizeSp,
                        readerTheme = prefs.readerTheme,
                        textBrightness = prefs.readerTextBrightness,
                        invertedScroll = prefs.invertedScroll,
                        focusModeEnabled = false,
                    ),
                    actions = ReaderMenuActions(
                        onFontSizeChange = {}, onThemeChange = {}, onTextBrightnessChange = {},
                        onInvertedScrollChange = {}, onFocusModeEnabledChange = {}, onSearch = {},
                        onAddBookmark = {}, onOpenBookmarks = {}, onShowToc = {}, onDismiss = {},
                    ),
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.reader_settings_title)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performTextInput("brightness")
        composeRule.onNodeWithText(text(R.string.settings_search_hint)).performImeAction()
        composeRule.onNodeWithText(text(R.string.reader_text_brightness_subtitle)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_text_brightness_title)).assertIsDisplayed()
        capture("in-reading-reader-target")
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("settings-search-review"))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(id: Int) = composeRule.activity.getString(id)

    private fun actions() = SettingsHomeActions(
        onOpenRsvp = {}, onOpenBionic = {}, onOpenReader = {}, onOpenFocus = {}, onOpenInfo = {},
        onCheckForUpdates = {}, onOpenLanguage = {}, onOpenStartingTutorial = {}, onReset = {}, onClose = {},
    )
}
