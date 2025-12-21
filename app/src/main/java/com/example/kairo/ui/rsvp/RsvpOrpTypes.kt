package com.example.kairo.ui.rsvp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal data class OrpTypography(
    val fontSizeSp: Float,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight
)

internal data class OrpColors(
    val textColor: Color,
    val pivotColor: Color,
    val pivotLineColor: Color
)

internal data class OrpTextLayout(
    val horizontalBias: Float,
    val lockPivot: Boolean
)

internal data class OrpTextContent(
    val fullText: String,
    val firstWordStart: Int,
    val firstWordEndExclusive: Int,
    val pivotPosition: Int,
    val wordCount: Int
)

internal data class OrpTextRendering(
    val annotatedText: AnnotatedString,
    val textStyle: TextStyle,
    val textMeasurer: TextMeasurer
)

internal data class OrpBounds(
    val safeLeftPx: Float,
    val safeRightPx: Float,
    val desiredPivotX: Float,
    val maxTranslationX: Float,
    val maxWidthPx: Float
)

internal data class OrpPivotRange(
    val start: Int,
    val end: Int,
    val safePivotIndex: Int
)

internal data class OrpLayoutResult(
    val pivotIndex: Int,
    val translationX: Float,
    val guideBias: Float
)

internal data class PivotAlignment(
    val pivotX: Float,
    val translationX: Float
)

internal data class OrpTextBuildState(
    var needsSpace: Boolean = false,
    var firstWordStart: Int = INVALID_INDEX,
    var firstWordEndExclusive: Int = INVALID_INDEX
)
