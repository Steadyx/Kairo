package com.kairo.reader.ui.settings

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsHomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun checkForUpdatesRow_requestsUpdateCheck() {
        var updateCheckRequested = false

        composeRule.setContent {
            KairoTheme {
                SettingsHomeScreen(
                    actions =
                    SettingsHomeActions(
                        onOpenRsvp = {},
                        onOpenBionic = {},
                        onOpenReader = {},
                        onOpenFocus = {},
                        onOpenInfo = {},
                        onCheckForUpdates = { updateCheckRequested = true },
                        onOpenLanguage = {},
                        onOpenStartingTutorial = {},
                        onReset = {},
                        onClose = {},
                    )
                )
            }
        }

        val checkForUpdatesTitle =
            composeRule.activity.getString(R.string.update_check_title)
        composeRule
            .onNodeWithText(checkForUpdatesTitle)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(updateCheckRequested)
        }
    }
}
