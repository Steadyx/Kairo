package com.kairo.reader.ui.settings

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsViewportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @After
    fun restoreActivityOrientation() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun landscapeSettingsKeepNavigationVisibleWhenScrolled() {
        showSettings(landscape = true, largeText = false)
        capture("settings-landscape")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_starting_tutorial_title))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
        capture("settings-landscape-scrolled")
    }

    @Test
    fun portraitSettingsShowReadingOptionsFirst() {
        showSettings(landscape = false, largeText = false)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_group_reading)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.rsvp_settings_title)).assertIsDisplayed()
        capture("settings-portrait")
    }

    @Test
    fun landscapeLargeTextKeepsLastSettingsRowReachable() {
        showSettings(landscape = true, largeText = true)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_starting_tutorial_title))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
        capture("settings-landscape-large-text")
    }

    private fun showSettings(landscape: Boolean, largeText: Boolean) {
        val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.resources.configuration.orientation == orientation
        }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else 1f)) {
                KairoTheme(readerTheme = if (largeText) ReaderTheme.DARK else ReaderTheme.SEPIA) {
                    SettingsHomeScreen(
                        actions = SettingsHomeActions(
                            onOpenRsvp = {}, onOpenBionic = {}, onOpenReader = {}, onOpenFocus = {},
                            onOpenInfo = {}, onCheckForUpdates = {}, onOpenLanguage = {},
                            onOpenStartingTutorial = {}, onReset = {}, onClose = {},
                        ),
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(composeRule.activity.getExternalFilesDir("ui-review"))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
