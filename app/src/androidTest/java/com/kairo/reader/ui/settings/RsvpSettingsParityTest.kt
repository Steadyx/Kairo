package com.kairo.reader.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
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

    @androidx.compose.runtime.Composable
    private fun SettingsFixture(prefs: UserPreferences, onBack: () -> Unit) {
        RsvpSettingsScreen(
            preferences = prefs,
            onSelectRsvpProfile = {}, onSaveRsvpProfile = { _, _ -> }, onDeleteRsvpProfile = {},
            onRsvpTempoMsPerWordChange = {}, onRsvpConfigChange = {}, onUnlockExtremeSpeedChange = {},
            onRsvpFontSizeChange = {}, onRsvpTextBrightnessChange = {}, onRsvpFontWeightChange = {},
            onRsvpFontFamilyChange = {}, onRsvpVerticalBiasChange = {}, onRsvpHorizontalBiasChange = {},
            onBack = onBack,
        )
    }
}
