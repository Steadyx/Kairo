package com.kairo.reader.ui.settings

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun backgroundAndFontDraftSurviveRecreationAndApplyTogether() {
        var applied: UserPreferences? = null
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            KairoTheme {
                ThemeSettingsScreen(UserPreferences(), onApply = { theme, custom, reader, interfaceFont, timed ->
                    applied = UserPreferences(
                        readerTheme = theme,
                        customTheme = custom,
                        readerFontFamily = reader,
                        interfaceFontFamily = interfaceFont,
                        rsvpFontFamily = timed
                    )
                }, onBack = {})
            }
        }
        capture("theme-studio-light")
        node(R.string.theme_tab_colours).performClick()
        rule.onNodeWithTag("theme-live-preview").assertIsDisplayed()
        capture("theme-studio-palette")
        node(R.string.theme_hex).performScrollTo().performTextReplacement("#172C29")
        rule.onNodeWithTag("theme-live-preview").assertIsDisplayed()
        node(R.string.theme_tab_fonts).performScrollTo().performClick()
        node(R.string.font_merriweather).performScrollTo().performClick()
        node(R.string.font_lora).performClick()
        restoration.emulateSavedInstanceStateRestore()
        node(R.string.font_lora).performScrollTo().assertIsDisplayed()
        node(R.string.theme_apply).performClick()
        rule.runOnIdle {
            assertEquals(ReaderTheme.CUSTOM, applied?.readerTheme)
            assertEquals(0xFF172C29.toInt(), applied?.customTheme?.background)
            assertEquals(RsvpFontFamily.LORA, applied?.readerFontFamily)
            assertEquals(RsvpFontFamily.INTER, applied?.rsvpFontFamily)
        }
    }

    @Test
    fun cancelRequiresExplicitDiscardAndDoesNotApply() {
        var closed = false
        var applied = false
        rule.setContent {
            KairoTheme {
                ThemeSettingsScreen(UserPreferences(), onApply = { _, _, _, _, _ -> applied = true }, onBack = { closed = true })
            }
        }
        node(R.string.theme_tab_colours).performClick()
        node(R.string.theme_harmony_triadic).performScrollTo().performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.action_back)).performClick()
        node(R.string.theme_keep_editing).performClick()
        rule.runOnIdle {
            assertFalse(closed)
            assertFalse(applied)
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.action_back)).performClick()
        node(R.string.theme_discard).performClick()
        rule.runOnIdle {
            assertTrue(closed)
            assertFalse(applied)
        }
    }

    @Test
    fun largeTextCanReachPaletteFontAndApplyControls() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                KairoTheme(readerTheme = ReaderTheme.DARK) {
                    ThemeSettingsScreen(
                        UserPreferences(
                            readerTheme = ReaderTheme.CUSTOM,
                            customTheme = CustomTheme(background = 0xFF172C29.toInt())
                        ),
                        onApply = { _, _, _, _, _ -> },
                        onBack = {}
                    )
                }
            }
        }
        node(R.string.theme_apply).assertIsDisplayed()
        rule.onNodeWithTag("theme-role-list").performScrollToNode(hasText(rule.activity.getString(R.string.theme_color_tertiary)))
        node(R.string.theme_color_tertiary).performClick()
        rule.onNodeWithTag("theme-live-preview").assertIsDisplayed()
        capture("theme-studio-large-text")
        node(R.string.theme_tab_fonts).performScrollTo().performClick()
        node(R.string.font_merriweather).performScrollTo().performClick()
        node(R.string.font_lexend).performScrollTo().performClick()
        node(R.string.font_lexend).assertIsDisplayed()
        node(R.string.theme_apply).assertIsDisplayed()
    }

    @Test
    fun colorPickerPreservesSaturationWhenPassingThroughBlack() {
        var saved: Int? = null
        rule.setContent {
            KairoTheme {
                ThemeColorEditor(ThemeColorRole.BACKGROUND, 0xFFFF0000.toInt(), false, { saved = it })
            }
        }
        rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        )[2].performSemanticsAction(SemanticsActions.SetProgress) {
            it(0f)
        }
        rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        )[2].performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.5f)
        }
        rule.runOnIdle { assertEquals(0xFF800000.toInt(), saved) }
    }

    @Test
    fun draggingKeepsPreviewAndControlsAnchored() {
        rule.setContent { KairoTheme { ThemeSettingsScreen(UserPreferences(), { _, _, _, _, _ -> }, {}) } }
        node(R.string.theme_tab_colours).performClick()
        val before = rule.onNodeWithTag("theme-controls").getUnclippedBoundsInRoot()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[2]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        rule.onNodeWithTag("theme-live-preview").assertIsDisplayed()
        node(R.string.theme_contrast_aa).assertIsDisplayed()
        val after = rule.onNodeWithTag("theme-controls").getUnclippedBoundsInRoot()
        assertEquals(before.top.value, after.top.value, 0.1f)
        capture("theme-studio-live-dark")
    }

    @Test
    fun incompleteHexBlocksApplyAndResetResetsEditorAsWellAsPreview() {
        rule.setContent { KairoTheme { ThemeSettingsScreen(UserPreferences(), { _, _, _, _, _ -> }, {}) } }
        node(R.string.theme_tab_colours).performClick()
        node(R.string.theme_hex).performScrollTo().performTextReplacement("#12")
        node(R.string.theme_apply).assertIsNotEnabled()
        node(R.string.theme_start_again).performClick()
        node(R.string.theme_hex).performScrollTo().assertTextContains("#F4EFE4")
        node(R.string.theme_apply).assertIsEnabled()
    }

    @After
    fun restoreOrientation() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    @Test
    fun landscapeKeepsPreviewBesideControls() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        rule.setContent { KairoTheme { ThemeSettingsScreen(UserPreferences(), { _, _, _, _, _ -> }, {}) } }
        node(R.string.theme_tab_colours).performClick()
        node(R.string.theme_hex).performScrollTo().assertIsDisplayed()
        val preview = rule.onNodeWithTag("theme-live-preview").getUnclippedBoundsInRoot()
        val controls = rule.onNodeWithTag("theme-controls").getUnclippedBoundsInRoot()
        assertTrue(preview.right <= controls.left)
        rule.onNodeWithTag("theme-live-preview").assertIsDisplayed()
        node(R.string.theme_apply).assertIsDisplayed()
        capture("theme-studio-landscape")
    }

    private fun node(resource: Int) = rule.onNodeWithText(rule.activity.getString(resource))

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
