package com.kairo.reader.ui.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.R
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.theme.KairoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ReaderParagraphAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun paragraphExposesClickLongClickAndSelectionActionsToTalkBack() {
        val selection = mutableStateOf<IntRange?>(null)
        var startedAt: Int? = null
        var timedReadingAt: Int? = null
        var extendedTo: Int? = null
        var cancelled = false
        composeRule.setContent {
            KairoTheme {
                ParagraphText(
                    state =
                    ParagraphTextState(
                        paragraph =
                        Paragraph(
                            tokens =
                            listOf(
                                Token("One", TokenType.WORD),
                                Token("two", TokenType.WORD),
                            ),
                            startIndex = 0,
                        ),
                        focusIndex = 0,
                        fontSizeSp = 18f,
                        textBrightness = 1f,
                        timedReadingMode = TimedReadingMode.RSVP,
                        selectionRange = selection.value,
                    ),
                    actions =
                    ParagraphTextActions(
                        onFocusChange = {},
                        onStartTimedReading = { timedReadingAt = it },
                        onSelectionStart = { tokenIndex ->
                            startedAt = tokenIndex
                            selection.value = tokenIndex..tokenIndex
                        },
                        onSelectionExtend = { extendedTo = it },
                        onSelectionCancel = { cancelled = true },
                    ),
                )
            }
        }

        val paragraph = composeRule.onNodeWithText("One two")
        paragraph.assertHasClickAction()
        paragraph.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(0, timedReadingAt) }
        paragraph.performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.runOnIdle { assertEquals(0, startedAt) }

        val extendLabel =
            composeRule.activity.getString(R.string.reader_extend_selection_forward_action)
        composeRule.runOnIdle {
            val actions = paragraph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertTrue(actions.first { it.label == extendLabel }.action())
        }
        composeRule.runOnIdle { assertEquals(1, extendedTo) }

        val cancelLabel = composeRule.activity.getString(R.string.reader_cancel_selection_action)
        composeRule.runOnIdle {
            val actions = paragraph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertTrue(actions.first { it.label == cancelLabel }.action())
        }
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun physicalTapUsesTouchedChapterLinkBeforeParagraphActions() {
        var selectedChapter: Int? = null
        var focusChanges = 0
        var timedReadingStarts = 0
        composeRule.setContent {
            KairoTheme {
                ParagraphText(
                    state =
                    ParagraphTextState(
                        paragraph =
                        Paragraph(
                            tokens =
                            listOf(
                                Token(
                                    text = "Chapter",
                                    type = TokenType.WORD,
                                    linkChapterIndex = 3,
                                ),
                                Token("One", TokenType.WORD),
                            ),
                            startIndex = 0,
                        ),
                        focusIndex = 0,
                        fontSizeSp = 18f,
                        textBrightness = 1f,
                        timedReadingMode = TimedReadingMode.RSVP,
                    ),
                    actions =
                    ParagraphTextActions(
                        onFocusChange = { focusChanges += 1 },
                        onStartTimedReading = { timedReadingStarts += 1 },
                        onChapterSelected = { selectedChapter = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Chapter One").performTapAtTextOffset(2)

        composeRule.runOnIdle {
            assertEquals(3, selectedChapter)
            assertEquals(0, focusChanges)
            assertEquals(0, timedReadingStarts)
        }
    }

    @Test
    fun physicalTapsFocusTouchedWordThenStartTimedReadingExactlyOnce() {
        val focusIndex = mutableStateOf(0)
        var timedReadingStarts = 0
        var timedReadingAt: Int? = null
        composeRule.setContent {
            KairoTheme {
                ParagraphText(
                    state =
                    ParagraphTextState(
                        paragraph =
                        Paragraph(
                            tokens =
                            listOf(
                                Token("One", TokenType.WORD),
                                Token("two", TokenType.WORD),
                            ),
                            startIndex = 0,
                        ),
                        focusIndex = focusIndex.value,
                        fontSizeSp = 18f,
                        textBrightness = 1f,
                        timedReadingMode = TimedReadingMode.RSVP,
                    ),
                    actions =
                    ParagraphTextActions(
                        onFocusChange = { focusIndex.value = it },
                        onStartTimedReading = {
                            timedReadingStarts += 1
                            timedReadingAt = it
                        },
                    ),
                )
            }
        }

        val paragraph = composeRule.onNodeWithText("One two")
        paragraph.performTapAtTextOffset(5)
        composeRule.runOnIdle {
            assertEquals(1, focusIndex.value)
            assertEquals(0, timedReadingStarts)
        }

        paragraph.performTapAtTextOffset(5)
        composeRule.runOnIdle {
            assertEquals(1, timedReadingStarts)
            assertEquals(1, timedReadingAt)
        }
    }

    @Test
    fun physicalLongPressFocusesAndSelectsTouchedWord() {
        val focusIndex = mutableStateOf(0)
        var selectionStart: Int? = null
        composeRule.setContent {
            KairoTheme {
                ParagraphText(
                    state =
                    ParagraphTextState(
                        paragraph =
                        Paragraph(
                            tokens =
                            listOf(
                                Token("One", TokenType.WORD),
                                Token("two", TokenType.WORD),
                            ),
                            startIndex = 0,
                        ),
                        focusIndex = focusIndex.value,
                        fontSizeSp = 18f,
                        textBrightness = 1f,
                        timedReadingMode = TimedReadingMode.RSVP,
                    ),
                    actions =
                    ParagraphTextActions(
                        onFocusChange = { focusIndex.value = it },
                        onStartTimedReading = {},
                        onSelectionStart = { selectionStart = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithText("One two").performLongPressAtTextOffset(5)

        composeRule.runOnIdle {
            assertEquals(1, focusIndex.value)
            assertEquals(1, selectionStart)
        }
    }

    @Test
    fun focusedParagraphActivatesTimedReadingOnceForHardwareKeys() {
        var timedReadingStarts = 0
        var timedReadingAt: Int? = null
        composeRule.setContent {
            KairoTheme {
                ParagraphText(
                    state =
                    ParagraphTextState(
                        paragraph =
                        Paragraph(
                            tokens = listOf(Token("One", TokenType.WORD)),
                            startIndex = 4,
                        ),
                        focusIndex = 4,
                        fontSizeSp = 18f,
                        textBrightness = 1f,
                        timedReadingMode = TimedReadingMode.RSVP,
                    ),
                    actions =
                    ParagraphTextActions(
                        onFocusChange = {},
                        onStartTimedReading = { tokenIndex ->
                            timedReadingStarts += 1
                            timedReadingAt = tokenIndex
                        },
                    ),
                )
            }
        }

        val paragraph = composeRule.onNodeWithText("One").requestFocus().assertIsFocused()
        listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)
            .forEachIndexed { index, key ->
                paragraph.performKeyInput {
                    keyDown(key)
                    keyUp(key)
                }
                composeRule.runOnIdle {
                    assertEquals(index + 1, timedReadingStarts)
                    assertEquals(4, timedReadingAt)
                }
            }
    }

    private fun SemanticsNodeInteraction.performTapAtTextOffset(offset: Int) {
        val layoutResult = textLayoutResult()
        performTouchInput { click(layoutResult.getBoundingBox(offset).center) }
    }

    private fun SemanticsNodeInteraction.performLongPressAtTextOffset(offset: Int) {
        val layoutResult = textLayoutResult()
        performTouchInput { longClick(layoutResult.getBoundingBox(offset).center) }
    }

    private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            action(results)
        }
        return results.single()
    }
}
