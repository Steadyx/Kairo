package com.kairo.reader.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.updates.ChangelogScreen
import com.kairo.reader.ui.updates.ReleaseInvitationDialog
import com.kairo.reader.ui.updates.releaseNotes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhatsNewDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun invitationOffersTourChangelogAndSkip() {
        var action = ""
        composeRule.setContent {
            KairoTheme {
                ReleaseInvitationDialog(
                    version = "1.4.0",
                    hasTour = true,
                    onSkip = { action = "skip" },
                    onStartTour = { action = "tour" },
                    onViewChangelog = { action = "changelog" },
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.whats_new_take_tour)).performClick()
        composeRule.runOnIdle { assertEquals("tour", action) }
        composeRule.onNodeWithText(text(R.string.whats_new_view_changelog)).performClick()
        composeRule.runOnIdle { assertEquals("changelog", action) }
        composeRule.onNodeWithText(text(R.string.action_skip)).performClick()
        composeRule.runOnIdle { assertEquals("skip", action) }
    }

    @Test
    fun changelogCanReplayTourAndReachOlderVersions() {
        var tours = 0
        composeRule.setContent {
            KairoTheme { ChangelogScreen(onBack = {}, onStartTour = { tours++ }) }
        }
        composeRule.onNodeWithText(text(R.string.whats_new_take_tour)).performClick()
        composeRule.runOnIdle { assertEquals(1, tours) }
        composeRule.onNodeWithTag("changelog").performScrollToNode(
            hasText(composeRule.activity.getString(R.string.whats_new_version, "1.0.8"))
        )
    }

    @Test
    fun tourActionsRemainReachableAtLargeFontSize() {
        var action = ""
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                KairoTheme {
                    StartingTutorialOverlay(
                        state = StartingTutorialOverlayState(releaseNotes.first().tour.first(), 0, 4) { action = "changelog" },
                        targetBounds = null,
                        onNext = { action = "next" },
                        onPrevious = {},
                        onSkip = { action = "skip" },
                    )
                }
            }
        }
        composeRule.onNodeWithText(text(R.string.whats_new_view_changelog)).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("changelog", action) }
        composeRule.onNodeWithText(text(R.string.action_skip)).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("skip", action) }
    }

    private fun text(id: Int) = composeRule.activity.getString(id)
}
