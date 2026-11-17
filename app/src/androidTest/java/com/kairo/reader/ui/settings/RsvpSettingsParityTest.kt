package com.kairo.reader.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.theme.KairoFocusedReadingTheme
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RsvpSettingsParityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun settingsRenderIdenticallyInsideFocusedReadingAndHomeThemes() {
        val readingHost = mutableStateOf(false)
        val prefs = UserPreferences()
        var backCount = 0
        composeRule.setContent {
            KairoTheme(readerTheme = prefs.readerTheme) {
                if (readingHost.value) {
                    KairoFocusedReadingTheme { SettingsFixture(prefs, onBack = { backCount++ }) }
                } else {
                    SettingsFixture(prefs, onBack = { backCount++ })
                }
            }
        }
        val home = composeRule.onRoot().captureToImage().asAndroidBitmap()
        composeRule.runOnIdle { readingHost.value = true }
        val reading = composeRule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("Full RSVP settings must not inherit the focused-reading typography or shapes", home.sameAs(reading))
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun readingDialogMatchesTheFullHomeSettingsPage() {
        val showDialog = mutableStateOf(false)
        val prefs = UserPreferences()
        val state = RsvpSettingsState(
            selectedProfileId = prefs.rsvpSelectedProfileId, customProfiles = prefs.rsvpCustomProfiles,
            config = prefs.rsvpConfig, tempoMsPerWord = prefs.rsvpTempoMsPerWord,
            unlockExtremeSpeed = prefs.unlockExtremeSpeed, fontSizeSp = prefs.rsvpFontSizeSp,
            textBrightness = prefs.rsvpTextBrightness, fontFamily = prefs.rsvpFontFamily,
            fontWeight = prefs.rsvpFontWeight, verticalBias = prefs.rsvpVerticalBias,
            horizontalBias = prefs.rsvpHorizontalBias,
        )
        val actions = RsvpSettingsActions(
            onSelectProfile = {}, onSaveCustomProfile = { _, _ -> }, onDeleteCustomProfile = {},
            onTempoMsPerWordChange = {}, onConfigChange = {}, onUnlockExtremeSpeedChange = {},
            onFontSizeChange = {}, onTextBrightnessChange = {}, onFontWeightChange = {}, onFontFamilyChange = {},
            onVerticalBiasChange = {}, onHorizontalBiasChange = {},
        )
        composeRule.setContent {
            KairoTheme(readerTheme = prefs.readerTheme) {
                RsvpSettingsPage(state, actions, prefs.readerTheme, onBack = {})
                if (showDialog.value) {
                    KairoFocusedReadingTheme {
                        RsvpSettingsDialog(state, actions, prefs.readerTheme, onBack = { showDialog.value = false })
                    }
                }
            }
        }
        val home = composeRule.onRoot().captureToImage().asAndroidBitmap()
        composeRule.runOnIdle { showDialog.value = true }
        val reading = composeRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("ui-review"))
        java.io.File(directory, "rsvp-settings-home.png").outputStream().use {
            home.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        java.io.File(directory, "rsvp-settings-reading.png").outputStream().use {
            reading.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue("RSVP settings must have identical layout in the reading dialog", home.sameAs(reading))
        composeRule.onNode(
            hasContentDescription(composeRule.activity.getString(R.string.action_back)) and hasAnyAncestor(isDialog())
        )
            .assertIsDisplayed().performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun readingBasicsCommitAndTimingDetailsStayUnderAdvanced() {
        val prefs = mutableStateOf(UserPreferences())
        composeRule.setContent {
            KairoTheme {
                SettingsFixture(
                    prefs.value,
                    onConfigChange = { update -> prefs.value = prefs.value.copy(rsvpConfig = update(prefs.value.rsvpConfig)) },
                    onBack = {},
                )
            }
        }
        val resources = composeRule.activity.resources
        val sliders = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        composeRule.onAllNodes(sliders).assertCountEquals(3)
        composeRule.onNodeWithContentDescription(resources.getString(R.string.rsvp_reading_speed_title)).assertExists()
        composeRule.onNodeWithContentDescription(resources.getString(R.string.rsvp_text_size_title)).assertExists()
        composeRule.onNodeWithContentDescription(resources.getString(R.string.rsvp_difficult_word_support_title))
            .performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        composeRule.runOnIdle { assertEquals(1.5, prefs.value.rsvpConfig.difficultWordSupport, 0.0) }
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_up_to_words, 3)).performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(prefs.value.rsvpConfig.enablePhraseChunking)
            assertEquals(3, prefs.value.rsvpConfig.maxWordsPerUnit)
        }
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_one_word)).performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(!prefs.value.rsvpConfig.enablePhraseChunking) }
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_adaptive_pacing_title)).assertDoesNotExist()
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("ui-review"))
        java.io.File(directory, "rsvp-comprehension-controls.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_difficult_word_support_title)).assertExists()
        composeRule.onNodeWithText(resources.getString(R.string.settings_advanced_title)).performScrollTo().performClick()
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_readability_floors_title)).performScrollTo().performClick()
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_word_parts_title)).performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(!prefs.value.rsvpConfig.showDifficultWordParts) }
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_min_word_time_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_long_word_min_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileSelectorAppearsAboveReadingBasicsAndOpensOnDemand() {
        val prefs = UserPreferences()
        composeRule.setContent { KairoTheme { SettingsFixture(prefs, onBack = {}) } }
        val resources = composeRule.activity.resources
        val save = composeRule.onNodeWithText(resources.getString(R.string.rsvp_profile_save_current))
        save.assertDoesNotExist()
        val profile = composeRule.onNodeWithText(resources.getString(R.string.rsvp_profile_title))
        val basics = composeRule.onNodeWithText(resources.getString(R.string.rsvp_quick_tune_title))
        assertTrue(profile.getUnclippedBoundsInRoot().bottom <= basics.getUnclippedBoundsInRoot().top)
        profile.assertIsDisplayed().performClick()
        save.performScrollTo().performClick()
        composeRule.onNodeWithText(resources.getString(R.string.rsvp_profile_save_title)).assertIsDisplayed()
    }

    @Test
    fun rhythmReopensWithSavedPhraseEndingsAndBackDiscardsAnEdit() {
        val prefs = mutableStateOf(UserPreferences())
        composeRule.setContent {
            KairoTheme {
                SettingsFixture(
                    prefs.value,
                    onConfigChange = { update -> prefs.value = prefs.value.copy(rsvpConfig = update(prefs.value.rsvpConfig)) },
                    onBack = {},
                )
            }
        }
        fun text(id: Int) = composeRule.activity.getString(id)
        fun openRhythm() = composeRule.onNodeWithText(text(R.string.rsvp_rhythm_calibration_title)).performScrollTo().performClick()
        val light = text(R.string.rsvp_rhythm_light)
        val strong = composeRule.onNode(hasText(text(R.string.rsvp_rhythm_strong)) and hasAnySibling(hasText(light)))
        val save = composeRule.onNodeWithText(text(R.string.action_save))

        openRhythm()
        save.assertIsNotEnabled()
        strong.performScrollTo().performClick()
        save.performClick()
        openRhythm()
        strong.assertIsSelected()
        save.assertIsNotEnabled()
        composeRule.onNodeWithText(light).performScrollTo().performClick()
        pressBack()
        openRhythm()
        strong.assertIsSelected()
    }

    @androidx.compose.runtime.Composable
    private fun SettingsFixture(
        prefs: UserPreferences,
        onConfigChange: ((RsvpConfig) -> RsvpConfig) -> Unit = {},
        onBack: () -> Unit,
    ) {
        RsvpSettingsScreen(
            preferences = prefs,
            onSelectRsvpProfile = {}, onSaveRsvpProfile = { _, _ -> }, onDeleteRsvpProfile = {},
            onRsvpTempoMsPerWordChange = {}, onRsvpConfigChange = onConfigChange, onUnlockExtremeSpeedChange = {},
            onRsvpFontSizeChange = {}, onRsvpTextBrightnessChange = {}, onRsvpFontWeightChange = {},
            onRsvpFontFamilyChange = {}, onRsvpVerticalBiasChange = {}, onRsvpHorizontalBiasChange = {},
            onBack = onBack,
        )
    }
}
