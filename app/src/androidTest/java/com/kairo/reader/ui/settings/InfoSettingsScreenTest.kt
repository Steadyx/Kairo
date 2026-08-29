package com.kairo.reader.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfoSettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun websiteRow_opensKairoWebsite() {
        var openedUri: String? = null
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    openedUri = uri
                }
            }

        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                KairoTheme {
                    InfoSettingsScreen(onBack = {})
                }
            }
        }

        val websiteTitle = composeRule.activity.getString(R.string.info_website_title)
        composeRule.onNodeWithText(websiteTitle).performClick()

        composeRule.runOnIdle {
            assertEquals("https://kairoreader.com", openedUri)
        }
    }

    @Test
    fun contactRow_opensEmailComposer() {
        var openedUri: String? = null
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    openedUri = uri
                }
            }

        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                KairoTheme {
                    InfoSettingsScreen(onBack = {})
                }
            }
        }

        val contactTitle = composeRule.activity.getString(R.string.info_contact_title)
        composeRule.onNodeWithText(contactTitle).performClick()

        composeRule.runOnIdle {
            assertEquals("mailto:kairoapp@proton.me", openedUri)
        }
    }
}
