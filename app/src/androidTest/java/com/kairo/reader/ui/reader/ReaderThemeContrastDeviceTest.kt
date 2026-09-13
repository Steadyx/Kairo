package com.kairo.reader.ui.reader

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kairo.reader.TestActivity
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.CustomTheme
import com.kairo.reader.core.model.HighlightColor
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationKind
import com.kairo.reader.core.model.TimedReadingMode
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.ui.saved.displayColor
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.theme.colorContrast
import com.kairo.reader.ui.theme.materialColorScheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderThemeContrastDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun renderedFocusedLinkWithOverlappingHighlightsUsesProtectedText() {
        val custom = CustomTheme(background = 0xFF747474.toInt())
        val saved = SavedAnnotation(
            "test", BookId("preview"), 0, 0, 0, "Remember", "", HighlightColor.YELLOW,
            SavedAnnotationKind.HIGHLIGHT, 0L, 0L
        )
        rule.setContent {
            KairoTheme(readerTheme = ReaderTheme.CUSTOM, customTheme = custom) {
                ParagraphText(
                    ParagraphTextState(
                        Paragraph(listOf(Token("Remember", TokenType.WORD, linkChapterIndex = 1)), 0),
                        0,
                        18f,
                        0.88f,
                        TimedReadingMode.RSVP,
                        savedAnnotations = listOf(saved),
                        selectionRange = 0..0,
                        searchMatchRange = 0..0
                    ),
                    ParagraphTextActions({}, {}),
                )
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Remember").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        rule.runOnIdle {
            val text = results.single().layoutInput.text
            val foreground = text.spanStyles.last().item.color
            val scheme = custom.materialColorScheme()
            val background = readerSpanBackground(
                scheme.background,
                listOf(
                    HighlightColor.YELLOW.displayColor().copy(alpha = 0.18f),
                    scheme.tertiary.copy(alpha = 0.18f),
                    scheme.primary.copy(alpha = 0.22f),
                    scheme.primary.copy(alpha = 0.16f),
                )
            )
            assertTrue(colorContrast(foreground, background) >= 4.5f)
        }
    }
}
