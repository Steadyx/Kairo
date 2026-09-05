package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun shortViewportReservesMostHeightForSettingsAndKeepsBackVisible() {
        var backClicked = false
        composeRule.setContent {
            KairoTheme {
                Box(modifier = Modifier.height(320.dp)) {
                    SettingsScaffold(title = "Settings", onBack = { backClicked = true }) { modifier ->
                        Column(modifier = modifier.testTag("settings-content")) {
                            Text("First setting")
                        }
                    }
                }
            }
        }
        val bounds = composeRule.onNodeWithTag("settings-content").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 180.dp)
        composeRule.onNodeWithText("First setting").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(backClicked) }
    }

    @Test
    fun settingsContentRespectsMaximumWidth() {
        composeRule.setContent {
            KairoTheme {
                SettingsScaffold(title = "Settings", onBack = {}, maxContentWidth = 280.dp) { modifier ->
                    Column(modifier = modifier.testTag("settings-content")) { Text("Setting") }
                }
            }
        }
        composeRule.onNodeWithTag("settings-content").assertWidthIsEqualTo(280.dp)
    }

    @Test
    fun detailedTuningStartsCollapsedAndRestoresExpandedState() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            KairoTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExpandableSettingsSection(title = "Display", summary = "Text and guide appearance") {
                        Text("Guide brightness")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Guide brightness").assertDoesNotExist()
        composeRule.onNodeWithText("Display").performClick()
        composeRule.onNodeWithText("Guide brightness").assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Guide brightness").assertIsDisplayed()
        composeRule.onNodeWithText("Display").performClick()
        composeRule.onNodeWithText("Guide brightness").assertDoesNotExist()
    }

    @Test
    fun largeTextSliderValueUsesAvailableWidthAndRemainsScrollable() {
        composeRule.setContent {
            KairoTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Column(
                        modifier = Modifier.width(280.dp).height(240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SettingsSliderRow(
                            title = "Reading speed",
                            valueLabel = "Comfortable reading pace 50%",
                            value = 50f,
                            onValueChange = {},
                            valueRange = 0f..100f,
                        )
                    }
                }
            }
        }
        val value = composeRule.onNodeWithText("Comfortable reading pace 50%")
            .performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(value.right - value.left > 84.dp)
    }
}
