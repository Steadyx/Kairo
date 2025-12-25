package com.example.kairo.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.shouldInsertSpaceBeforeToken
import com.example.kairo.ui.theme.MerriweatherFontFamily

@Composable
internal fun ParagraphText(
    paragraph: Paragraph,
    focusIndex: Int,
    fontSizeSp: Float,
    textBrightness: Float,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
) {
    val baseStyle =
        TextStyle(
            fontFamily = MerriweatherFontFamily,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.6f).sp,
            color = MaterialTheme.colorScheme.onBackground.copy(
                alpha = textBrightness.coerceIn(0.55f, 1.0f)
            ),
        )
    val paragraphIndent =
        remember(fontSizeSp) {
            ParagraphStyle(textIndent = TextIndent(firstLine = (fontSizeSp * 0.7f).sp))
        }
    val primary = MaterialTheme.colorScheme.primary
    val focusStyle =
        remember(primary) {
            SpanStyle(
                fontWeight = FontWeight.SemiBold,
                color = primary,
                textDecoration = TextDecoration.Underline,
            )
        }

    val annotated =
        remember(paragraph.tokens, paragraph.startIndex, focusIndex, primary, paragraphIndent) {
            buildAnnotatedString {
                paragraph.tokens.forEachIndexed { localIndex, token ->
                    if (token.type == TokenType.PARAGRAPH_BREAK ||
                        token.type == TokenType.PAGE_BREAK
                    ) {
                        return@forEachIndexed
                    }
                    val globalIndex = paragraph.startIndex + localIndex

                    val prevToken = if (localIndex > 0) paragraph.tokens[localIndex - 1] else null
                    val needsSpaceBefore =
                        shouldInsertSpaceBeforeToken(token, prevToken, localIndex)

                    if (needsSpaceBefore) append(" ")

                    val start = length
                    append(token.text)
                    val end = length

                    addStringAnnotation(
                        tag = "tokenIndex",
                        annotation = globalIndex.toString(),
                        start = start,
                        end = end
                    )
                    if (globalIndex == focusIndex) addStyle(focusStyle, start, end)
                }
                addStyle(paragraphIndent, start = 0, end = length)
            }
        }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = baseStyle,
        modifier =
        Modifier
            .fillMaxWidth()
            .pointerInput(annotated, focusIndex) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position).coerceIn(
                            0,
                            (
                                annotated.length -
                                    1
                                ).coerceAtLeast(0)
                        )
                        val hit =
                            annotated.getStringAnnotations(
                                "tokenIndex",
                                offset,
                                offset
                            ).firstOrNull()
                                ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex ==
                            focusIndex
                        ) {
                            onStartRsvp(tokenIndex)
                        } else {
                            onFocusChange(tokenIndex)
                        }
                    },
                    onLongPress = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position).coerceIn(
                            0,
                            (
                                annotated.length -
                                    1
                                ).coerceAtLeast(0)
                        )
                        val hit =
                            annotated.getStringAnnotations(
                                "tokenIndex",
                                offset,
                                offset
                            ).firstOrNull()
                                ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex != focusIndex) onFocusChange(tokenIndex)
                        onStartRsvp(tokenIndex)
                    },
                )
            },
        onTextLayout = { layoutResult = it },
    )
}
