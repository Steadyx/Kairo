package com.example.kairo.ui.rsvp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.ui.theme.InterFontFamily
import com.example.kairo.ui.theme.RobotoFontFamily
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RsvpScreen(
    tokens: List<Token>,
    startIndex: Int,
    config: RsvpConfig,
    engine: RsvpEngine,
    fontSizeSp: Float = 44f,
    fontFamily: RsvpFontFamily = RsvpFontFamily.INTER,
    fontWeight: RsvpFontWeight = RsvpFontWeight.LIGHT,
    verticalBias: Float = -0.15f,
    onFinished: (Int) -> Unit,
    onPositionChanged: (Int) -> Unit,  // Called to save position (no navigation)
    onWpmChange: (Int) -> Unit,  // Called when WPM is adjusted via swipe
    onVerticalBiasChange: (Float) -> Unit, // Called when vertical position is adjusted
    onExit: (Int) -> Unit  // Called to navigate back with resume index
) {
    // Resolve font family
    val resolvedFontFamily: FontFamily = when (fontFamily) {
        RsvpFontFamily.INTER -> InterFontFamily
        RsvpFontFamily.ROBOTO -> RobotoFontFamily
    }

    // Resolve font weight
    val resolvedFontWeight: FontWeight = when (fontWeight) {
        RsvpFontWeight.LIGHT -> FontWeight.Light
        RsvpFontWeight.NORMAL -> FontWeight.Normal
        RsvpFontWeight.MEDIUM -> FontWeight.Medium
    }
    // WPM adjustment state - use local state to avoid recomposition from config changes
    var currentWpm by remember { mutableStateOf(config.baseWpm) }
    var showWpmIndicator by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragStartWpm by remember { mutableStateOf(config.baseWpm) }
    var currentVerticalBias by remember { mutableStateOf(verticalBias) }

    // Create a local config copy that uses currentWpm for frame generation
    // This prevents frames from regenerating when only WPM changes
    val localConfig = remember(config) { config }.copy(baseWpm = currentWpm)

    // Only regenerate frames when tokens or startIndex change, NOT when WPM changes
    // WPM only affects timing, which is computed per-frame during playback
    val frames = remember(tokens, startIndex, config.wordsPerFrame, config.maxChunkLength) {
        runCatching { engine.generateFrames(tokens, startIndex, config) }.getOrElse { emptyList() }
    }

    var frameIndex by rememberSaveable { mutableStateOf(0) }
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var isAdjustingPosition by remember { mutableStateOf(false) }
    var isPositioningMode by rememberSaveable { mutableStateOf(false) }
    var dragStartBias by remember { mutableStateOf(verticalBias) }
    var wasPlayingBeforePositioning by rememberSaveable { mutableStateOf(true) }

    // Helper to get current token position from the frame's originalTokenIndex
    fun getCurrentTokenIndex(): Int {
        return frames.getOrNull(frameIndex)?.originalTokenIndex ?: startIndex
    }

    fun exitAndSavePosition() {
        // Freeze playback immediately so we don't advance after exit.
        isPlaying = false
        completed = true

        val currentIndex = getCurrentTokenIndex()
        val safeIndex = if (tokens.isNotEmpty()) {
            tokens.nearestWordIndex(currentIndex)
        } else {
            currentIndex
        }
        onPositionChanged(safeIndex)
        onExit(safeIndex)
    }

    fun enterPositioningMode() {
        wasPlayingBeforePositioning = isPlaying
        isPlaying = false
        showControls = false
        showQuickSettings = false
        showWpmIndicator = false
        isPositioningMode = true
        isAdjustingPosition = true
    }

    fun finishPositioning(resumeIfWasPlaying: Boolean = true) {
        if (!isPositioningMode) return
        isPositioningMode = false
        isAdjustingPosition = false
        onVerticalBiasChange(currentVerticalBias)
        if (resumeIfWasPlaying && wasPlayingBeforePositioning && !completed) {
            isPlaying = true
        }
    }

    BackHandler {
        exitAndSavePosition()
    }

    // Save position whenever frame changes (so it's always up to date)
    LaunchedEffect(frameIndex) {
        if (frames.isNotEmpty()) {
            val currentIndex = getCurrentTokenIndex()
            val safeIndex = if (tokens.isNotEmpty()) {
                tokens.nearestWordIndex(currentIndex)
            } else {
                currentIndex
            }
            onPositionChanged(safeIndex)
        }
    }

    // Reset state only when tokens or startIndex change (not when config/WPM changes)
    LaunchedEffect(tokens, startIndex) {
        frameIndex = 0
        isPlaying = true
        completed = false
        // Initialize WPM from config when starting a new RSVP session
        currentWpm = config.baseWpm
        // Initialize vertical bias from prefs on new session
        currentVerticalBias = verticalBias
        // Clear transient UI modes on new session
        isPositioningMode = false
        isAdjustingPosition = false
        showQuickSettings = false
        showControls = false
    }

    // Auto-hide controls after delay when playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Auto-hide WPM indicator after a short delay
    LaunchedEffect(showWpmIndicator) {
        if (showWpmIndicator) {
            delay(1500)
            showWpmIndicator = false
        }
    }

    // Empty state
    if (frames.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable { exitAndSavePosition() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No content to display.\nTap to go back.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        return
    }

    // Store the original WPM used to generate frames for duration scaling
    val originalWpm = remember(tokens, startIndex) { config.baseWpm }

    // RSVP playback logic with dynamic WPM adjustment
    LaunchedEffect(isPlaying, frameIndex, completed, currentWpm) {
        if (!isPlaying || completed) return@LaunchedEffect
        if (frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[frameIndex]

        // Scale duration based on WPM ratio: higher WPM = shorter duration
        // duration ∝ 1/WPM, so newDuration = originalDuration * (originalWpm / currentWpm)
        val scaledDuration = (frame.durationMs * originalWpm.toDouble() / currentWpm.toDouble()).toLong()
            .coerceAtLeast(30L) // Minimum 30ms per frame

        delay(scaledDuration)
        if (frameIndex == frames.lastIndex) {
            completed = true
            // Move to next token after the last frame's original token
            val rawNextIndex = frame.originalTokenIndex + 1
            val safeNextIndex = if (tokens.isNotEmpty()) {
                tokens.nearestWordIndex(rawNextIndex)
            } else {
                rawNextIndex
            }
            onFinished(safeNextIndex)
        } else {
            frameIndex++
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    // Swipe threshold for WPM adjustment (pixels needed for 10 WPM change)
    val wpmSwipeThreshold = 30f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Restart gesture handler when persisted WPM changes, so we don't use stale config.
            .pointerInput(config.baseWpm, isPositioningMode) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        dragStartWpm = currentWpm
                        dragStartBias = currentVerticalBias
                    },
                    onDragEnd = {
                        if (isPositioningMode) {
                            if (currentVerticalBias != dragStartBias) {
                                onVerticalBiasChange(currentVerticalBias)
                            }
                        } else {
                            // Persist the WPM change when drag ends
                            if (currentWpm != dragStartWpm) {
                                onWpmChange(currentWpm)
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (isPositioningMode) {
                            // Move ORP vertically. dragAmount > 0 when dragging down.
                            val biasPerPx = 0.0015f
                            currentVerticalBias =
                                (currentVerticalBias + dragAmount * biasPerPx).coerceIn(-0.6f, 0.6f)
                            isAdjustingPosition = true
                        } else {
                            dragAccumulator += dragAmount
                            // Calculate WPM change: swipe up = decrease WPM, swipe down = increase WPM
                            val wpmDelta = -(dragAccumulator / wpmSwipeThreshold).toInt() * 10
                            if (wpmDelta != 0) {
                                val newWpm = (dragStartWpm + wpmDelta).coerceIn(100, 800)
                                if (newWpm != currentWpm) {
                                    currentWpm = newWpm
                                    showWpmIndicator = true
                                }
                            }
                        }
                    }
                )
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isPositioningMode) {
                        // Exit positioning mode on tap.
                        finishPositioning()
                    } else {
                        // Tapping pauses and shows controls
                        isPlaying = false
                        showControls = true
                    }
                },
                onLongClick = {
                    // Long press exits RSVP mode
                    exitAndSavePosition()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main focus word display with ORP guide
        val currentFrame = frames.getOrNull(frameIndex)
        val pivotColor = MaterialTheme.colorScheme.primary
        val bracketColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        // Subtle line color - less vibrant than the pivot character
        val pivotLineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

        // The focus word(s) - rendered with ORP alignment and pivot markers
        // Offset upward by ~20% from center using padding
        currentFrame?.let { frame ->
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = BiasAlignment(
                    horizontalBias = 0f,
                    verticalBias = currentVerticalBias.coerceIn(-0.7f, 0.7f)
                )
            ) {
                OrpAlignedText(
                    tokens = frame.tokens,
                    pivotColor = pivotColor,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    bracketColor = bracketColor,
                    pivotLineColor = pivotLineColor,
                    fontSizeSp = fontSizeSp,
                    fontFamily = resolvedFontFamily,
                    fontWeight = resolvedFontWeight
                )
            }
        }

        // Horizontal guide line to preview vertical position while adjusting.
        AnimatedVisibility(
            visible = showQuickSettings || isAdjustingPosition || isPositioningMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = BiasAlignment(
                    horizontalBias = 0f,
                    verticalBias = currentVerticalBias.coerceIn(-0.7f, 0.7f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                )
            }
        }

        // Minimal progress indicator at bottom
        LinearProgressIndicator(
            progress = { (frameIndex + 1).toFloat() / frames.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )

        // Top bar with close and settings buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Settings button - subtle
            IconButton(onClick = {
                if (isPositioningMode) {
                    // In swipe-only positioning mode, settings acts as "Done".
                    finishPositioning()
                } else {
                    showQuickSettings = !showQuickSettings
                    if (showQuickSettings) showControls = false
                }
            }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
            // Close button
            IconButton(onClick = { exitAndSavePosition() }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // WPM indicator - shows when adjusting via swipe
        AnimatedVisibility(
            visible = showWpmIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 60.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$currentWpm WPM",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Positioning mode indicator
        AnimatedVisibility(
            visible = isPositioningMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 60.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        finishPositioning()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Positioning mode • swipe to move\nTap here to finish",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Quick settings panel
        AnimatedVisibility(
            visible = showQuickSettings,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Quick Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Positioning mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Positioning mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isPositioningMode,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                enterPositioningMode()
                            } else {
                                finishPositioning(resumeIfWasPlaying = false)
                            }
                        }
                    )
                }

                // WPM slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "WPM: $currentWpm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(90.dp)
                    )
                    Slider(
                        value = currentWpm.toFloat(),
                        onValueChange = { currentWpm = it.toInt() },
                        onValueChangeFinished = { onWpmChange(currentWpm) },
                        valueRange = 100f..800f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "Swipe up/down to adjust WPM\nEnable Positioning mode to swipe text\nLong press to exit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Bottom controls - slide up when visible
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Playback controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            frameIndex = (frameIndex - 1).coerceAtLeast(0)
                            completed = false
                        }
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            isPlaying = !isPlaying
                            if (isPlaying) showControls = false
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            if (frameIndex < frames.lastIndex) {
                                frameIndex++
                            } else {
                                val lastFrame = frames.getOrNull(frameIndex)
                                val rawNextIndex = (lastFrame?.originalTokenIndex ?: startIndex) + 1
                                val safeNextIndex = if (tokens.isNotEmpty()) {
                                    tokens.nearestWordIndex(rawNextIndex)
                                } else {
                                    rawNextIndex
                                }
                                onFinished(safeNextIndex)
                                completed = true
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress info
                Text(
                    "${frameIndex + 1} / ${frames.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Tap anywhere to resume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}


/**
 * Composable that displays text with ORP (Optimal Recognition Point) alignment.
 * Uses a single Text with AnnotatedString for zero-jitter rendering.
 * The pivot character is always at the exact center of the screen.
 */
@Composable
private fun OrpAlignedText(
    tokens: List<Token>,
    pivotColor: Color,
    textColor: Color,
    bracketColor: Color,
    pivotLineColor: Color,
    fontSizeSp: Float = 44f,
    fontFamily: FontFamily = InterFontFamily,
    fontWeight: FontWeight = FontWeight.Light
) {
    // Build the display string with sections
    val firstWord = tokens.firstOrNull { it.type == TokenType.WORD }
    val orpIndex = firstWord?.orpIndex ?: 0

    // Build full text with punctuation attached correctly
    // Opening punctuation attaches to the next word (no space after)
    // Closing punctuation attaches to the previous word (no space before)
    val openingPunctuation = setOf('(', '[', '{', '"', '\u201C', '\u2018')

    val fullText = buildString {
        var needsSpace = false
        tokens.forEach { token ->
            when (token.type) {
                TokenType.WORD -> {
                    if (needsSpace) append(" ")
                    append(token.text)
                    needsSpace = true
                }
                TokenType.PUNCTUATION -> {
                    val isOpening = token.text.length == 1 && token.text[0] in openingPunctuation
                    // No space before closing punctuation
                    // Space before opening punctuation (if not at start)
                    if (isOpening && needsSpace) append(" ")
                    append(token.text)
                    // Opening punctuation: no space after (next word attaches directly)
                    // Closing punctuation: space after (next word is separate)
                    needsSpace = !isOpening
                }
                TokenType.PARAGRAPH_BREAK -> { /* ignore */ }
            }
        }
    }

    // Calculate the pivot position in the full string
    val pivotPosition = if (firstWord != null) {
        val wordStart = fullText.indexOf(firstWord.text)
        if (wordStart >= 0) wordStart + orpIndex else orpIndex
    } else {
        0
    }

    val beforePivot = if (pivotPosition > 0) fullText.substring(0, pivotPosition) else ""
    val pivotChar = if (pivotPosition < fullText.length) fullText[pivotPosition].toString() else ""
    val afterPivot = if (pivotPosition + 1 < fullText.length) fullText.substring(pivotPosition + 1) else ""

    // Use fixed font size for consistent visual experience
    // Long words will naturally fit due to the ORP centering and padding
    val textStyle = MaterialTheme.typography.displayMedium.copy(
        fontFamily = fontFamily,
        fontSize = fontSizeSp.sp,
        fontWeight = fontWeight,
        letterSpacing = 0.5.sp
    )

    // Pad both sides to equal length so pivot stays centered
    val maxSideLength = maxOf(beforePivot.length, afterPivot.length)
    val paddedBefore = beforePivot.padStart(maxSideLength)
    val paddedAfter = afterPivot.padEnd(maxSideLength)

    // Build annotated string with pivot highlighted
    val annotatedText = buildAnnotatedString {
        withStyle(SpanStyle(color = textColor)) {
            append(paddedBefore)
        }
        withStyle(SpanStyle(color = pivotColor)) {
            append(pivotChar)
        }
        withStyle(SpanStyle(color = textColor)) {
            append(paddedAfter)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Top static line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(pivotLineColor)
        )

        // Vertical pointer down
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp)
                .background(pivotLineColor)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Single text element - no layout shifts, no jitter
        Text(
            text = annotatedText,
            style = textStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical pointer up
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp)
                .background(pivotLineColor)
        )

        // Bottom static line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(pivotLineColor)
        )
    }
}
