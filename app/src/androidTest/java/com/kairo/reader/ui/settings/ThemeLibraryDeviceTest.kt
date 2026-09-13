package com.kairo.reader.ui.settings

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.core.model.ThemeDesign
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.ui.theme.KairoTheme
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeLibraryDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun savedDesignAndUndoSurviveRecreationThenApplyTogether() {
        var saved: List<SavedTheme>? = null
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            KairoTheme {
                ThemeSettingsScreen(
                    UserPreferences(readerFontFamily = RsvpFontFamily.LORA),
                    onApply = { _, themes -> saved = themes },
                    onBack = {}
                )
            }
        }
        node(R.string.theme_save_new).performScrollTo().performClick()
        node(R.string.theme_name).performTextReplacement("Evening")
        node(R.string.theme_done).performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Evening").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_manage_named, "Evening")).performClick()
        node(R.string.theme_delete).performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_undo)).performClick()
        rule.onNodeWithText("Evening").performScrollTo().assertIsDisplayed()
        capture("theme-library")
        node(R.string.theme_apply).performClick()
        rule.runOnIdle {
            assertEquals("Evening", saved?.single()?.name)
            assertEquals(RsvpFontFamily.LORA, saved?.single()?.design?.readerFont)
        }
    }

    @Test
    fun duplicateRenameAndColourResetKeepTheOriginalAndItsFonts() {
        val original = SavedTheme(
            "first",
            "Original",
            ThemeDesign(
                custom = CustomTheme(background = 0xFF172C29.toInt()),
                readerFont = RsvpFontFamily.LORA
            )
        )
        var result: List<SavedTheme>? = null
        var appearance: ThemeDesign? = null
        rule.setContent {
            KairoTheme {
                ThemeSettingsScreen(UserPreferences(savedThemes = listOf(original)), { design, saved ->
                    appearance = design
                    result = saved
                }, {})
            }
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_manage_named, "Original")).performScrollTo().performClick()
        node(R.string.theme_duplicate).performClick()
        node(R.string.theme_name).performTextReplacement("Second")
        node(R.string.theme_done).performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_manage_named, "Second")).performScrollTo().performClick()
        node(R.string.theme_rename).performClick()
        node(R.string.theme_name).performTextReplacement("Renamed")
        node(R.string.theme_done).performClick()
        rule.onNodeWithText("Renamed").performScrollTo().performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_reset_options)).performClick()
        node(R.string.theme_reset_colours).performClick()
        node(R.string.theme_apply).performClick()
        rule.runOnIdle {
            assertEquals(listOf("Original", "Renamed"), result?.map { it.name })
            assertEquals(original, result?.first())
            assertEquals(CustomTheme(), appearance?.custom)
            assertEquals(RsvpFontFamily.LORA, appearance?.readerFont)
        }
    }

    @Test
    fun undoRestoresColourFieldAndPreviewTabsWork() {
        rule.setContent {
            KairoTheme {
                ThemeSettingsScreen(UserPreferences(readerTheme = ReaderTheme.CUSTOM), { _, _ -> }, {})
            }
        }
        node(R.string.theme_hex).performScrollTo().performTextReplacement("#172C29")
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_undo)).performClick()
        node(R.string.theme_hex).performScrollTo().assertTextContains("#F4EFE4")
        rule.onNodeWithTag("theme-preview-RSVP").performClick()
        node(R.string.theme_preview_paused).performScrollTo().assertIsDisplayed()
        capture("theme-rsvp-preview")
        rule.onNodeWithTag("theme-preview-INTERFACE").performScrollTo().performClick()
        node(R.string.theme_preview_continue).performScrollTo().assertIsDisplayed()
        capture("theme-interface-preview")
    }

    @Test
    fun failedSaveKeepsDraftOpenAndCanBeRetried() {
        var fail = true
        var closed = false
        var applied: ThemeDesign? = null
        rule.setContent {
            KairoTheme {
                ThemeSettingsScreen(UserPreferences(readerTheme = ReaderTheme.CUSTOM), { design, _ ->
                    if (fail) throw IOException("Test storage error")
                    applied = design
                }, { closed = true })
            }
        }
        node(R.string.theme_hex).performScrollTo().performTextReplacement("#172C29")
        node(R.string.theme_apply).performClick()
        node(R.string.theme_save_error).assertIsDisplayed()
        rule.runOnIdle {
            assertFalse(closed)
            fail = false
        }
        node(R.string.theme_apply).performClick()
        rule.runOnIdle { assertEquals(0xFF172C29.toInt(), applied?.custom?.background) }
    }

    @Test
    fun invalidColourCannotBeAppliedFromAnotherTabAndRevertClearsIt() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            KairoTheme { ThemeSettingsScreen(UserPreferences(readerTheme = ReaderTheme.CUSTOM), { _, _ -> }, {}) }
        }
        node(R.string.theme_hex).performScrollTo().performTextReplacement("#12")
        node(R.string.theme_tab_fonts).performClick()
        restoration.emulateSavedInstanceStateRestore()
        node(R.string.theme_apply).assertIsNotEnabled()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.theme_reset_options)).performClick()
        node(R.string.theme_revert).performClick()
        node(R.string.theme_apply).assertIsEnabled()
        node(R.string.theme_tab_colours).performClick()
        node(R.string.theme_hex).performScrollTo().assertTextContains("#F4EFE4")
    }

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.cacheDir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun node(id: Int) = rule.onNodeWithText(rule.activity.getString(id))
}
