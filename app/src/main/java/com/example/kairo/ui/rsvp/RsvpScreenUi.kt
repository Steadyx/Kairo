@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.TokenType
import com.example.kairo.ui.theme.InterFontFamily
import com.example.kairo.ui.theme.RobotoFontFamily
import kotlin.math.roundToLong

@Composable
internal fun RsvpPlaybackSurface(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val currentFrame = frames.getOrNull(runtime.frameIndex)
    val typography = OrpTypography(
        fontSizeSp = runtime.currentFontSizeSp,
        fontFamily = resolveFontFamily(runtime.currentFontFamily),
        fontWeight = resolveFontWeight(runtime.currentFontWeight)
    )
    val colors = rememberRsvpTextColors(runtime.currentTextBrightness)
    val interactionSource = remember { MutableInteractionSource() }
    val estimatedWpm = rememberEstimatedWpm(frames, context.timing.tempoScale)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .rsvpGestureModifier(context, interactionSource),
        contentAlignment = Alignment.Center
    ) {
        RsvpFocusWord(context, currentFrame, typography, colors)
        RsvpPositionGuide(context)
        RsvpProgressBar(context)
        RsvpTopBar(context)
        RsvpTempoIndicator(context, estimatedWpm)
        RsvpFontSizeIndicator(context)
        RsvpPositioningIndicator(context)
        RsvpQuickSettingsPanel(context, estimatedWpm)
        RsvpBottomControls(context)
    }
}

@Composable
private fun rememberEstimatedWpm(
    frames: List<com.example.kairo.core.model.RsvpFrame>,
    tempoScale: Double
): Int {
    val baseFrameStats = remember(frames) {
        val wordCount = frames.sumOf { frame -> frame.tokens.count { it.type == TokenType.WORD } }
            .coerceAtLeast(MIN_WORD_COUNT)
        val totalMs = frames.sumOf { it.durationMs }.coerceAtLeast(MIN_TOTAL_MS)
        wordCount to totalMs
    }
    return remember(baseFrameStats, tempoScale) {
        val wordCount = baseFrameStats.first
        val totalMs = (baseFrameStats.second * tempoScale)
            .roundToLong()
            .coerceAtLeast(MIN_TOTAL_MS)
        ((wordCount * MS_PER_MINUTE) / totalMs.toDouble()).toInt().coerceAtLeast(MIN_WORD_COUNT)
    }
}

@Composable
private fun rememberRsvpTextColors(textBrightness: Float): OrpColors {
    val clampedBrightness = textBrightness.coerceIn(TEXT_BRIGHTNESS_MIN, TEXT_BRIGHTNESS_MAX)
    val pivotLineAlpha = (PIVOT_LINE_ALPHA_BASE * clampedBrightness)
        .coerceIn(PIVOT_LINE_ALPHA_MIN, PIVOT_LINE_ALPHA_MAX)
    return OrpColors(
        pivotColor = MaterialTheme.colorScheme.primary,
        pivotLineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = pivotLineAlpha),
        textColor = MaterialTheme.colorScheme.onBackground.copy(alpha = clampedBrightness)
    )
}

@Composable
private fun RsvpFocusWord(
    context: RsvpUiContext,
    frame: com.example.kairo.core.model.RsvpFrame?,
    typography: OrpTypography,
    colors: OrpColors
) {
    val runtime = context.runtime
    val profile = context.state.profile
    if (frame == null) return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = BiasAlignment(
            horizontalBias = CENTER_BIAS,
            verticalBias = runtime.currentVerticalBias.coerceIn(
                VERTICAL_BIAS_MIN,
                VERTICAL_BIAS_MAX
            )
        )
    ) {
        OrpAlignedText(
            tokens = frame.tokens,
            typography = typography,
            colors = colors,
            layout = OrpTextLayout(
                horizontalBias = runtime.currentHorizontalBias,
                lockPivot = profile.config.enablePhraseChunking &&
                    profile.config.maxWordsPerUnit > ORP_LOCK_PIVOT_WORDS
            )
        )
    }
}

@Composable
private fun RsvpPositionGuide(context: RsvpUiContext) {
    val runtime = context.runtime
    val visible =
        runtime.showQuickSettings || runtime.isAdjustingPosition || runtime.isPositioningMode

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = BiasAlignment(
                horizontalBias = CENTER_BIAS,
                verticalBias = runtime.currentVerticalBias.coerceIn(
                    VERTICAL_BIAS_MIN,
                    VERTICAL_BIAS_MAX
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(POSITION_GUIDE_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = POSITIONING_LINE_ALPHA)
                    )
            )
        }
    }
}

private fun resolveFontFamily(fontFamily: RsvpFontFamily): FontFamily {
    return when (fontFamily) {
        RsvpFontFamily.INTER -> InterFontFamily
        RsvpFontFamily.ROBOTO -> RobotoFontFamily
    }
}

private fun resolveFontWeight(fontWeight: RsvpFontWeight): FontWeight {
    return when (fontWeight) {
        RsvpFontWeight.LIGHT -> FontWeight.Light
        RsvpFontWeight.NORMAL -> FontWeight.Normal
        RsvpFontWeight.MEDIUM -> FontWeight.Medium
    }
}
