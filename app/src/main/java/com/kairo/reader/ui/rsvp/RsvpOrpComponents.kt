@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

@Composable
internal fun OrpStaticLine(
    color: Color,
    thickness: Dp,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

@Composable
internal fun OrpPointer(
    guideBias: Float,
    color: Color,
    thickness: Dp,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(ORP_POINTER_HEIGHT),
    ) {
        Box(
            modifier =
            Modifier
                .align(BiasAlignment(horizontalBias = guideBias, verticalBias = CENTER_BIAS))
                .width(thickness)
                .fillMaxHeight()
                .background(color),
        )
    }
}

@Composable
internal fun OrpTextLine(
    annotatedText: AnnotatedString,
    textStyle: TextStyle,
    textColor: Color,
    translationX: Float,
    alpha: Float = 1f,
) {
    val renderedTranslationX = snapTranslationToRenderPixel(translationX)
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        Text(
            text = annotatedText,
            style = textStyle,
            color = textColor,
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    transformOrigin =
                        TransformOrigin(
                            ORP_TRANSFORM_ORIGIN_X,
                            ORP_TRANSFORM_ORIGIN_Y,
                        )
                    this.alpha = alpha.coerceIn(0f, 1f)
                    this.translationX = renderedTranslationX
                }
                .testTag("rsvp-focus-word"),
        )
    }
}
