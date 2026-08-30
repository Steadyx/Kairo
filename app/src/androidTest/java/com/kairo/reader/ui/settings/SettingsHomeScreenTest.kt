package com.kairo.reader.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialSteps
import org.junit.Assert.assertEquals
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
                    actions = settingsHomeActions(onCheckForUpdates = { updateCheckRequested = true })
                )
            }
        }

        val checkForUpdatesTitle =
            composeRule.activity.getString(R.string.update_check_title)
        composeRule
            .onNodeWithText(checkForUpdatesTitle)
            .performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(76.dp)
            .performClick()

        composeRule.runOnIdle {
            assertTrue(updateCheckRequested)
        }
    }

    @Test
    fun prominentNavRow_hasClickSemanticsAndMinimumHeight() {
        var clickCount = 0

        composeRule.setContent {
            KairoTheme {
                SettingsNavRow(
                    title = "Prominent row",
                    subtitle = "Prominent subtitle",
                    icon = Icons.Default.Settings,
                    presentation = SettingsNavRowPresentation.PROMINENT,
                    onClick = { clickCount += 1 },
                )
            }
        }

        composeRule
            .onNodeWithText("Prominent row")
            .assertHasClickAction()
            .assertHeightIsAtLeast(72.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }

    @Test
    fun settingsNavRow_omittedPresentationMatchesExplicitCompactBounds() {
        var defaultClickCount = 0
        var explicitClickCount = 0

        composeRule.setContent {
            KairoTheme {
                Column {
                    SettingsNavRow(
                        title = "Default compact row",
                        icon = Icons.Default.Settings,
                        onClick = { defaultClickCount += 1 },
                    )
                    SettingsNavRow(
                        title = "Explicit compact row",
                        icon = Icons.Default.Settings,
                        presentation = SettingsNavRowPresentation.COMPACT,
                        onClick = { explicitClickCount += 1 },
                    )
                }
            }
        }

        val defaultRow =
            composeRule
                .onNodeWithText("Default compact row")
                .assertHasClickAction()
        val explicitRow =
            composeRule
                .onNodeWithText("Explicit compact row")
                .assertHasClickAction()
        val defaultBounds = defaultRow.getUnclippedBoundsInRoot()
        val explicitBounds = explicitRow.getUnclippedBoundsInRoot()

        assertEquals(
            explicitBounds.right - explicitBounds.left,
            defaultBounds.right - defaultBounds.left,
        )
        assertEquals(
            explicitBounds.bottom - explicitBounds.top,
            defaultBounds.bottom - defaultBounds.top,
        )

        defaultRow.performClick()
        explicitRow.performClick()

        composeRule.runOnIdle {
            assertEquals(1, defaultClickCount)
            assertEquals(1, explicitClickCount)
        }
    }

    @Test
    fun finalTutorialTarget_isBroughtIntoConstrainedViewportAtLargeText() {
        val steps = startingTutorialSteps(includeReaderAndRsvp = false)
        val targetStepIndex =
            steps.indexOfFirst { it.targetId == StartingTutorialTargetIds.SETTINGS_TUTORIAL }
        val targetTutorialState =
            StartingTutorialOverlayState(
                step = steps[targetStepIndex],
                index = targetStepIndex,
                totalSteps = steps.size,
            )
        val tutorialState = mutableStateOf<StartingTutorialOverlayState?>(null)

        composeRule.setContent {
            KairoTheme {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(currentDensity.density, fontScale = 2f)
                ) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                            .testTag(SETTINGS_VIEWPORT_TAG),
                    ) {
                        SettingsHomeScreen(
                            actions = settingsHomeActions(),
                            tutorialState = tutorialState.value,
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag(SETTINGS_VIEWPORT_TAG)
        val targetTitle =
            composeRule.activity.getString(R.string.settings_starting_tutorial_title)
        val target = composeRule.onNodeWithText(targetTitle)
        val initialViewportBounds = viewport.getUnclippedBoundsInRoot()
        val initialTargetBounds = target.getUnclippedBoundsInRoot()
        assertTrue(initialTargetBounds.bottom > initialViewportBounds.bottom)

        composeRule.runOnIdle {
            tutorialState.value = targetTutorialState
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val viewportBounds = viewport.getUnclippedBoundsInRoot()
            val targetBounds = target.getUnclippedBoundsInRoot()
            targetBounds.top >= viewportBounds.top && targetBounds.bottom <= viewportBounds.bottom
        }

        val finalViewportBounds = viewport.getUnclippedBoundsInRoot()
        val finalTargetBounds = target.getUnclippedBoundsInRoot()
        assertTrue(finalTargetBounds.top >= finalViewportBounds.top)
        assertTrue(finalTargetBounds.bottom <= finalViewportBounds.bottom)
    }

    private fun settingsHomeActions(
        onCheckForUpdates: () -> Unit = {},
    ): SettingsHomeActions =
        SettingsHomeActions(
            onOpenRsvp = {},
            onOpenBionic = {},
            onOpenReader = {},
            onOpenFocus = {},
            onOpenInfo = {},
            onCheckForUpdates = onCheckForUpdates,
            onOpenLanguage = {},
            onOpenStartingTutorial = {},
            onReset = {},
            onClose = {},
        )

    private companion object {
        const val SETTINGS_VIEWPORT_TAG = "settings_home_viewport"
    }
}
