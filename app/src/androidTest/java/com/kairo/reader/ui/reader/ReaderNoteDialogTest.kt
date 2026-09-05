package com.kairo.reader.ui.reader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderNoteDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun distinguishesUserNoteFromSourcePassageAndRequiresNoteText() {
        var savedNote: String? = null
        composeRule.setContent {
            KairoTheme {
                ReaderNoteDialog(
                    selectedText = "A passage from the book",
                    onSave = { note, _ -> savedNote = note },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.reader_note_hint),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.saved_note_passage),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("A passage from the book").assertIsDisplayed()

        val saveLabel = composeRule.activity.getString(R.string.action_save_note)
        composeRule.onNodeWithText(saveLabel).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("My interpretation")
        composeRule.onNodeWithText(saveLabel).assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals("My interpretation", savedNote) }
    }

    @Test
    fun noteDraftSurvivesSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            KairoTheme {
                ReaderNoteDialog(
                    selectedText = "A passage from the book",
                    onSave = { _, _ -> },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("Draft interpretation")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Draft interpretation"))
        )
    }
}
