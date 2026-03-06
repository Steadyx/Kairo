@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.effectiveBlinkMode
import com.example.kairo.core.model.isMidSentencePunctuation
import com.example.kairo.core.model.isSentenceEndingPunctuation
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.core.model.wordFloorMsForReadability
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

@Composable
internal fun RsvpPositionSaveEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    LaunchedEffect(runtime.frameIndex) {
        if (frames.isEmpty()) return@LaunchedEffect
        val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
        val currentResumeCursor =
            resolveCurrentResumeCursor(
                frames = frames,
                frameIndex = runtime.frameIndex,
                fallbackCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1,
            )
        runtime.currentTokenIndex = currentIndex
        runtime.currentResumeCursor = currentResumeCursor
        val now = SystemClock.elapsedRealtime()
        val shouldSave =
            now - runtime.lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS ||
                runtime.frameIndex == frames.lastIndex
        if (shouldSave) {
            runtime.lastPositionSaveMs = now
            context.callbacks.playback.onPositionChanged(currentResumePoint(context))
        }
    }
}

@Composable
internal fun RsvpFrameAlignmentEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames

    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        runtime.frameIndex = alignFrameIndex(frames, runtime.currentTokenIndex, runtime.currentResumeCursor)
    }
}

@Composable
internal fun RsvpSessionResetEffect(context: RsvpUiContext, sessionKey: String) {
    val runtime = context.runtime
    val book = context.state.book
    val profile = context.state.profile
    val textStyle = context.state.textStyle
    val layoutBias = context.state.layoutBias
    val frames = context.frameState.frames

    var lastSessionKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionKey) {
        if (lastSessionKey == sessionKey) return@LaunchedEffect
        lastSessionKey = sessionKey
        runtime.currentTokenIndex = book.startIndex
        runtime.currentResumeCursor = book.startResumeCursor.takeIf { it >= 0 } ?: -1
        runtime.frameIndex = alignFrameIndex(frames, book.startIndex, runtime.currentResumeCursor)
        runtime.rampStartFrameIndex = runtime.frameIndex
        runtime.scheduledFrameIndex = -1
        runtime.nextFrameAtMs = 0L
        runtime.isPlaying = true
        runtime.completed = false
        runtime.currentTempoMsPerWord = profile.config.tempoMsPerWord
        runtime.currentFontSizeSp = textStyle.fontSizeSp
        runtime.currentFontWeight = textStyle.fontWeight
        runtime.currentFontFamily = textStyle.fontFamily
        runtime.currentTextBrightness = textStyle.textBrightness
        runtime.currentVerticalBias = layoutBias.verticalBias
        runtime.currentHorizontalBias = layoutBias.horizontalBias
        runtime.dragStartBias = layoutBias.verticalBias
        runtime.dragStartHorizontalBias = layoutBias.horizontalBias
        runtime.isPositioningMode = false
        runtime.isAdjustingPosition = false
        runtime.isScrubbing = false
        runtime.isExiting = false
        runtime.dragAxis = RsvpDragAxis.NONE
        runtime.dragAccumulator = ZERO_FLOAT
        runtime.dragAccumulatorX = ZERO_FLOAT
        runtime.showQuickSettings = false
        runtime.showControls = false
    }
}

@Composable
internal fun RsvpPlaybackLoopEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val tokens = context.state.book.tokens
    val tempoScale = context.timing.tempoScale
    val config = context.state.profile.config

    LaunchedEffect(runtime.isPlaying, runtime.frameIndex, runtime.completed, frames, tempoScale) {
        if (!runtime.isPlaying || runtime.completed) return@LaunchedEffect
        if (runtime.frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[runtime.frameIndex]
        val effectiveTempoMs = (config.tempoMsPerWord * tempoScale).roundToLong()
        val effectiveBlinkMode = config.effectiveBlinkMode(effectiveTempoMs)
        if (effectiveBlinkMode == com.example.kairo.core.model.BlinkMode.OFF &&
            shouldSkipBlinkFrame(frame, tempoScale, config)
        ) {
            if (runtime.frameIndex >= frames.lastIndex) {
                completePlayback(context)
            } else {
                runtime.frameIndex += 1
            }
            return@LaunchedEffect
        }
        val rampMultiplier =
            rampMultiplier(config, runtime.frameIndex, runtime.rampStartFrameIndex)
        val resumeDelayMs =
            resumeDelayMs(config, runtime.frameIndex, runtime.rampStartFrameIndex)
        val frameMs =
            (frame.durationMs * rampMultiplier)
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)
        var scaledMs =
            ((frameMs + resumeDelayMs) * tempoScale)
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)
        val floorMs = frameFloorMs(frame, config, effectiveTempoMs)
        if (scaledMs < floorMs) {
            scaledMs = floorMs
        }
        val now = SystemClock.elapsedRealtime()
        val chained = runtime.scheduledFrameIndex == runtime.frameIndex - 1
        val overshootMs =
            if (chained && runtime.nextFrameAtMs > 0L) {
                (now - runtime.nextFrameAtMs).coerceAtLeast(0L)
            } else {
                0L
            }
        val candidateTarget =
            if (chained && runtime.nextFrameAtMs > 0L) {
                runtime.nextFrameAtMs + scaledMs
            } else {
                now + scaledMs
            }
        val softenedCatchUp =
            if (overshootMs > 0L) {
                (overshootMs * (1.0 - CATCH_UP_FACTOR)).roundToLong()
            } else {
                0L
            }
        val targetMs =
            if (candidateTarget < now) {
                now + scaledMs
            } else {
                candidateTarget + softenedCatchUp
            }
        runtime.scheduledFrameIndex = runtime.frameIndex
        runtime.nextFrameAtMs = targetMs
        val delayMs = (targetMs - now).coerceAtLeast(MIN_FRAME_DELAY_MS)
        delay(delayMs)
        if (runtime.frameIndex == frames.lastIndex) {
            completePlayback(context)
        } else {
            runtime.frameIndex += 1
        }
    }
}

private fun rampMultiplier(
    config: com.example.kairo.core.model.RsvpConfig,
    frameIndex: Int,
    rampStartIndex: Int,
): Double {
    val rampFrames = config.rampUpFrames
    if (rampStartIndex <= 0 || rampStartIndex < rampFrames) return 1.0
    val offset = frameIndex - rampStartIndex
    if (rampFrames <= 0 || offset < 0 || offset >= rampFrames) return 1.0
    val progress = offset.toDouble() / rampFrames.toDouble().coerceAtLeast(1.0)
    return 1.35 - (0.35 * progress)
}

private fun resumeDelayMs(
    config: RsvpConfig,
    frameIndex: Int,
    rampStartIndex: Int,
): Long {
    if (rampStartIndex <= 0 ||
        rampStartIndex < config.rampUpFrames ||
        frameIndex != rampStartIndex
    ) {
        return 0L
    }
    return config.startDelayMs
}

private const val CATCH_UP_FACTOR = 0.25

private fun frameFloorMs(
    frame: com.example.kairo.core.model.RsvpFrame,
    config: RsvpConfig,
    effectiveTempoMs: Long,
): Long {
    val tokens = frame.tokens
    if (tokens.isEmpty()) return frame.durationMs

    val firstWordIndex = tokens.indexOfFirst { it.type == TokenType.WORD }
    if (firstWordIndex == -1) return frame.durationMs

    var total = 0L
    tokens.forEachIndexed { index, token ->
        when (token.type) {
            TokenType.WORD -> total += wordFloorMs(token, config, effectiveTempoMs)
            TokenType.PUNCTUATION ->
                total += punctuationFloorMs(tokens, token, index, firstWordIndex, config)
            TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> Unit
        }
    }
    return max(total, MIN_FRAME_DELAY_MS)
}

private fun wordFloorMs(
    word: Token,
    config: RsvpConfig,
    effectiveTempoMs: Long,
): Long = config.wordFloorMsForReadability(word, effectiveTempoMs)

private fun punctuationFloorMs(
    tokens: List<Token>,
    token: Token,
    index: Int,
    firstWordIndex: Int,
    config: RsvpConfig,
): Long {
    val ch = token.text.firstOrNull() ?: return 0L
    if (index < firstWordIndex && isOpeningPunctuationChar(ch)) return 0L

    val prevToken = tokens.getOrNull(index - 1)
    val nextToken = tokens.getOrNull(index + 1)
    val prevCh = prevToken?.text?.firstOrNull()
    val prevIsSentenceEnd =
        prevCh != null && (isSentenceEndingPunctuation(prevCh) || prevCh == '.')
    val isSentenceEnd = isSentenceEndingPunctuation(ch) || ch == '.'
    if (isSentenceEnd && prevIsSentenceEnd) return 0L
    if (isQuoteOrBracket(ch) &&
        (prevToken?.type == TokenType.PUNCTUATION || nextToken?.type == TokenType.PUNCTUATION)
    ) {
        return 0L
    }

    val prevWord = tokens.subList(0, index).lastOrNull { it.type == TokenType.WORD }
    val prevText = prevWord?.text.orEmpty()

    val pauseFloorScale = config.minPauseScale.coerceIn(0.0, 1.0)
    val sentenceFloor = (config.sentenceEndPauseMs * pauseFloorScale).roundToLong()
    val periodFloor = (config.periodPauseMs * pauseFloorScale).roundToLong()
    val commaFloor = (config.commaPauseMs * pauseFloorScale).roundToLong()
    val semicolonFloor = (config.semicolonPauseMs * pauseFloorScale).roundToLong()
    val colonFloor = (config.colonPauseMs * pauseFloorScale).roundToLong()
    val dashFloor = (config.dashPauseMs * pauseFloorScale).roundToLong()
    val parenFloor = (config.parenthesesPauseMs * pauseFloorScale).roundToLong()
    val quoteFloor = (config.quotePauseMs * pauseFloorScale).roundToLong()
    val ellipsisFloor = commaFloor

    return when {
        ch == '.' ->
            if (isDecimalPoint(prevText, nextToken) || isThousandSeparator(prevText, nextToken)) {
                0L
            } else {
                periodFloor
            }
        ch == '\u2026' -> ellipsisFloor
        isSentenceEndingPunctuation(ch) -> sentenceFloor
        ch == ',' -> if (isThousandSeparator(prevText, nextToken)) 0L else commaFloor
        ch == ';' -> semicolonFloor
        ch == ':' -> colonFloor
        ch == '\u2014' || ch == '\u2013' || ch == '-' -> dashFloor
        ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '{' || ch == '}' -> parenFloor
        ch == '"' || ch == '\u201C' || ch == '\u201D' || ch == '\u2018' || ch == '\u2019' -> quoteFloor
        isMidSentencePunctuation(ch) -> commaFloor
        else -> 0L
    }
}

private fun isOpeningPunctuationChar(ch: Char): Boolean = ch == '"' || ch in ORP_OPENING_PUNCTUATION

private fun isQuoteOrBracket(ch: Char): Boolean =
    ch == '"' ||
        ch == '\u201C' ||
        ch == '\u201D' ||
        ch == '\u2018' ||
        ch == '\u2019' ||
        ch == '(' ||
        ch == ')' ||
        ch == '[' ||
        ch == ']' ||
        ch == '{' ||
        ch == '}'

private fun isDecimalPoint(
    prevText: String,
    nextToken: Token?,
): Boolean {
    if (!prevText.any { it.isDigit() }) return false
    val nextText = nextToken?.text ?: return false
    return nextText.any { it.isDigit() }
}

private fun isThousandSeparator(
    prevText: String,
    nextToken: Token?,
): Boolean {
    if (prevText.isEmpty() || nextToken?.type != TokenType.WORD) return false
    if (!prevText.all { it.isDigit() }) return false
    val nextText = nextToken.text
    return nextText.length == 3 && nextText.all { it.isDigit() }
}

private fun shouldSkipBlinkFrame(
    frame: com.example.kairo.core.model.RsvpFrame,
    tempoScale: Double,
    config: RsvpConfig,
): Boolean {
    val effectiveTempoMs = (config.tempoMsPerWord * tempoScale).roundToLong()
    if (effectiveTempoMs >= BLINK_SKIP_TEMPO_MS) return false
    if (frame.tokens.any { it.type == TokenType.WORD }) return false
    if (frame.tokens.size != 1) return false
    val token = frame.tokens.first()
    if (token.type != TokenType.PUNCTUATION || token.text != " ") return false
    val scaledMs = (frame.durationMs * tempoScale).roundToLong()
    return scaledMs <= BLINK_SKIP_MAX_MS
}

private const val BLINK_SKIP_TEMPO_MS = 200L
private const val BLINK_SKIP_MAX_MS = 48L

@Composable
internal fun RsvpAutoHideControlsEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showControls, runtime.isPlaying) {
        if (runtime.showControls && runtime.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            runtime.showControls = false
        }
    }
}

@Composable
internal fun RsvpAutoHideTempoIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showTempoIndicator) {
        if (runtime.showTempoIndicator) {
            delay(TEMPO_INDICATOR_HIDE_DELAY_MS)
            runtime.showTempoIndicator = false
        }
    }
}

@Composable
internal fun RsvpAutoHideFontSizeIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showFontSizeIndicator) {
        if (runtime.showFontSizeIndicator) {
            delay(FONT_SIZE_INDICATOR_HIDE_DELAY_MS)
            runtime.showFontSizeIndicator = false
        }
    }
}
