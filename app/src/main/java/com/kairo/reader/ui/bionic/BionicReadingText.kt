@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
)

package com.kairo.reader.ui.bionic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.core.model.BionicReadingPreferences
import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.TokenType
import com.kairo.reader.core.model.shouldInsertSpaceBeforeToken
import com.kairo.reader.ui.rememberWindowContainerMetrics
import com.kairo.reader.ui.rsvp.RsvpRuntimeState
import com.kairo.reader.ui.rsvp.resolveFontFamily
import kotlin.math.ceil

internal const val BIONIC_MIN_FIXATION_STRENGTH = 0.30f
internal const val BIONIC_MAX_FIXATION_STRENGTH = 0.70f
internal const val BIONIC_MIN_HIGHLIGHT_STRENGTH = 0.08f
internal const val BIONIC_MAX_HIGHLIGHT_STRENGTH = 0.32f
internal const val BIONIC_DARK_MAX_HIGHLIGHT_STRENGTH = 0.24f
internal const val BIONIC_MIN_FONT_SIZE_SP = 18f
internal const val BIONIC_MAX_FONT_SIZE_SP = 40f
internal const val BIONIC_PANE_LINES = 5
internal const val BIONIC_COMPACT_PANE_LINES = 3
internal const val BIONIC_MIN_PANE_LINES = 1

@Composable
internal fun BionicReadingText(
    frames: List<RsvpFrame>,
    tokens: List<Token>,
    frameIndex: Int,
    runtime: RsvpRuntimeState,
    preferences: BionicReadingPreferences,
) {
    if (frames.isEmpty() || tokens.isEmpty()) return

    val windowMetrics = rememberWindowContainerMetrics()
    val density = LocalDensity.current
    val screenWidthDp = windowMetrics.roundedWidthDp
    val screenHeightDp = windowMetrics.roundedHeightDp
    val paneLineCount =
        bionicPaneLineCount(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            fontSizeSp = runtime.currentFontSizeSp,
            fontScale = density.fontScale,
        )
    val wordCapacity =
        remember(
            runtime.currentFontSizeSp,
            density.fontScale,
            screenWidthDp,
            paneLineCount,
        ) {
            estimateBionicWordCapacity(
                screenWidthDp = screenWidthDp,
                fontSizeSp = runtime.currentFontSizeSp,
                fontScale = density.fontScale,
                paneLineCount = paneLineCount,
            )
        }
    val characterCapacity =
        remember(
            runtime.currentFontSizeSp,
            density.fontScale,
            screenWidthDp,
            paneLineCount,
        ) {
            estimateBionicCharacterCapacity(
                screenWidthDp = screenWidthDp,
                fontSizeSp = runtime.currentFontSizeSp,
                paneLineCount = paneLineCount,
                fontScale = density.fontScale,
            )
        }
    val effectiveChunkWords =
        remember(wordCapacity) { resolveBionicTargetWordCount(wordCapacity = wordCapacity) }
    val chunks =
        remember(frames, tokens, effectiveChunkWords, wordCapacity, characterCapacity) {
            buildBionicTextChunks(
                frames = frames,
                tokens = tokens,
                targetWordCount = effectiveChunkWords,
                maximumWordCount = wordCapacity,
                maximumCharacterCount = characterCapacity,
            )
        }
    if (chunks.isEmpty()) return

    val safeFrameIndex = frameIndex.coerceIn(0, frames.lastIndex)
    val currentChunkIndex =
        chunks
            .indexOfFirst { safeFrameIndex in it.startFrameIndex until it.endFrameIndexExclusive }
            .takeIf { it >= 0 }
            ?: chunks.lastIndex
    val currentFrame = frames.getOrNull(safeFrameIndex)
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val textBrightness = runtime.currentTextBrightness.coerceIn(0.55f, 1f)
    val textColor =
        bionicBodyColor(
            onBackgroundColor = colorScheme.onBackground,
            onSurfaceVariantColor = colorScheme.onSurfaceVariant,
            backgroundColor = colorScheme.background,
            textBrightness = textBrightness,
        )
    val fixationColor =
        bionicFixationColor(
            onBackgroundColor = colorScheme.onBackground,
            backgroundColor = colorScheme.background,
            textBrightness = textBrightness,
        )
    val activeBackground =
        colorScheme.primary.copy(
            alpha = bionicHighlightAlpha(preferences.highlightStrength, colorScheme.background),
        )
    val chunkSurfaceColor =
        if (isDarkTheme) {
            colorScheme.surfaceContainerLow
        } else {
            colorScheme.surface.copy(alpha = 0.78f)
        }
    val safeFontSize =
        runtime.currentFontSizeSp.coerceIn(BIONIC_MIN_FONT_SIZE_SP, BIONIC_MAX_FONT_SIZE_SP)
    val lineHeightSp = safeFontSize * 1.48f
    val paneHeight = with(density) { lineHeightSp.sp.toDp() } * paneLineCount.toFloat() + 44.dp
    val minimumTopPadding =
        if (windowMetrics.isLandscape) 20.dp else 32.dp
    val paneTopPadding =
        (((windowMetrics.heightDp - paneHeight) / 2f) - 12.dp)
            .coerceAtLeast(minimumTopPadding)
            .coerceAtMost(112.dp)

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val chunk = chunks[currentChunkIndex]
        BionicChunkCard(
            text =
            buildBionicAnnotatedText(
                tokens = tokens,
                chunk = chunk,
                activeFrame = currentFrame,
                fixationStrength = preferences.fixationStrength,
                fixationColor = fixationColor,
                activeBackground = activeBackground,
            ),
            textStyle =
            TextStyle(
                fontSize = safeFontSize.sp,
                lineHeight = lineHeightSp.sp,
                fontFamily = resolveFontFamily(runtime.currentFontFamily),
                fontWeight = FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Start,
            ),
            surfaceColor = chunkSurfaceColor,
            paneLineCount = paneLineCount,
            modifier = Modifier.padding(top = paneTopPadding),
        )
    }
}

@Composable
private fun BionicChunkCard(
    text: AnnotatedString,
    textStyle: TextStyle,
    surfaceColor: Color,
    paneLineCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
        modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = textStyle,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            minLines = paneLineCount,
        )
    }
}

internal fun buildBionicAnnotatedText(
    tokens: List<Token>,
    chunk: BionicTextChunk,
    activeFrame: RsvpFrame?,
    fixationStrength: Float,
    fixationColor: Color,
    activeBackground: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val safeStart = chunk.startTokenIndex.coerceIn(0, tokens.size)
    val safeEnd = chunk.endTokenIndexExclusive.coerceIn(safeStart, tokens.size)
    var tokenIndexInParagraph = 0
    var previousToken: Token? = null
    var previousWasBreak = false
    var activeHighlightStart: Int? = null
    var activeHighlightEnd = 0

    for (tokenIndex in safeStart until safeEnd) {
        val token = tokens[tokenIndex]
        if (token.type == TokenType.PARAGRAPH_BREAK || token.type == TokenType.PAGE_BREAK) {
            if (builder.length > 0 && !previousWasBreak) builder.append("\n\n")
            tokenIndexInParagraph = 0
            previousToken = null
            previousWasBreak = true
            continue
        }

        val insertsSpace =
            shouldInsertSpaceBeforeToken(token, previousToken, tokenIndexInParagraph)
        if (insertsSpace) {
            builder.append(' ')
        }
        val tokenTextStart = builder.length
        builder.append(token.text)
        val tokenTextEnd = builder.length
        val activeRange = activeRangeForToken(activeFrame, tokenIndex, token.text.length)

        if (token.type == TokenType.WORD && token.text.isNotEmpty()) {
            val fixationEnd =
                tokenTextStart + bionicFixationEndOffset(token.text, fixationStrength)
            builder.addStyle(
                SpanStyle(color = fixationColor, fontWeight = FontWeight.Bold),
                tokenTextStart,
                fixationEnd.coerceAtMost(tokenTextEnd),
            )
        }

        activeRange?.let { range ->
            val highlightStart = tokenTextStart + range.first
            val highlightEnd = tokenTextStart + range.last + 1
            if (highlightEnd > highlightStart) {
                activeHighlightStart = activeHighlightStart ?: highlightStart
                activeHighlightEnd = highlightEnd.coerceAtMost(tokenTextEnd)
            }
        }

        tokenIndexInParagraph += 1
        previousToken = token
        previousWasBreak = false
    }

    activeHighlightStart?.let { start ->
        if (activeHighlightEnd > start) {
            builder.addStyle(
                SpanStyle(background = activeBackground),
                start,
                activeHighlightEnd,
            )
        }
    }

    return builder.toAnnotatedString()
}

internal fun bionicBodyColor(
    onBackgroundColor: Color,
    onSurfaceVariantColor: Color,
    backgroundColor: Color,
    textBrightness: Float,
): Color {
    val brightness = textBrightness.coerceIn(0.55f, 1f)
    val baseColor =
        if (backgroundColor.luminance() < 0.5f) {
            onSurfaceVariantColor
        } else {
            onBackgroundColor
        }
    return baseColor.copy(alpha = brightness)
}

internal fun bionicFixationColor(
    onBackgroundColor: Color,
    backgroundColor: Color,
    textBrightness: Float,
): Color {
    val brightness = textBrightness.coerceIn(0.55f, 1f)
    val fixationBrightness =
        if (backgroundColor.luminance() < 0.5f) {
            (brightness + 0.08f).coerceAtMost(1f)
        } else {
            brightness
        }
    return onBackgroundColor.copy(alpha = fixationBrightness)
}

internal fun bionicHighlightAlpha(
    highlightStrength: Float,
    backgroundColor: Color,
): Float {
    val maximum =
        if (backgroundColor.luminance() < 0.5f) {
            BIONIC_DARK_MAX_HIGHLIGHT_STRENGTH
        } else {
            BIONIC_MAX_HIGHLIGHT_STRENGTH
        }
    return highlightStrength.coerceIn(BIONIC_MIN_HIGHLIGHT_STRENGTH, maximum)
}

internal fun bionicFixationEndOffset(
    text: String,
    fixationStrength: Float,
): Int {
    if (text.isEmpty()) return 0
    val codePointCount = text.codePointCount(0, text.length)
    val fixationCodePoints =
        ceil(
            codePointCount *
                fixationStrength.coerceIn(
                    BIONIC_MIN_FIXATION_STRENGTH,
                    BIONIC_MAX_FIXATION_STRENGTH,
                ),
        ).toInt().coerceIn(1, codePointCount)
    return text.offsetByCodePoints(0, fixationCodePoints)
}

private fun activeRangeForToken(
    frame: RsvpFrame?,
    tokenIndex: Int,
    tokenLength: Int,
): IntRange? {
    if (
        frame == null ||
        tokenIndex !in frame.displayOriginalStartIndex until frame.displayOriginalEndExclusive
    ) {
        return null
    }
    val start =
        if (tokenIndex == frame.displayOriginalStartIndex) {
            frame.displayOriginalStartCharacterOffset
        } else {
            0
        }.coerceIn(0, tokenLength)
    val endExclusive =
        if (tokenIndex == frame.displayOriginalEndExclusive - 1) {
            frame.displayOriginalEndCharacterOffset ?: tokenLength
        } else {
            tokenLength
        }.coerceIn(start, tokenLength)
    return if (endExclusive > start) start until endExclusive else null
}
