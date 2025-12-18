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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpCustomProfile
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.RsvpFrame
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.core.rsvp.RsvpEngine
import com.example.kairo.ui.settings.RsvpSettingsContent
import com.example.kairo.ui.settings.SettingsNavRow
import com.example.kairo.ui.settings.SettingsSliderRow
import com.example.kairo.ui.settings.SettingsSwitchRow
import com.example.kairo.ui.settings.ThemeSelector
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
    selectedProfileId: String,
    customProfiles: List<RsvpCustomProfile>,
    extremeSpeedUnlocked: Boolean,
    onExtremeSpeedUnlockedChange: (Boolean) -> Unit,
    engine: RsvpEngine,
    readerTheme: ReaderTheme,
    focusModeEnabled: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onAddBookmark: (tokenIndex: Int, previewText: String) -> Unit,
    onOpenBookmarks: () -> Unit,
    fontSizeSp: Float = 44f,
    fontFamily: RsvpFontFamily = RsvpFontFamily.INTER,
    fontWeight: RsvpFontWeight = RsvpFontWeight.LIGHT,
    textBrightness: Float = 0.88f,
    verticalBias: Float = -0.15f,
    horizontalBias: Float = -0.12f,
    onFinished: (Int) -> Unit,
    onPositionChanged: (Int) -> Unit,  // Called to save position (no navigation)
    onTempoChange: (Long) -> Unit,  // Called when tempo is adjusted via swipe/slider
    onSelectProfile: (String) -> Unit, // Called when RSVP profile changes
    onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
    onRsvpConfigChange: (RsvpConfig) -> Unit, // Called when RSVP timing profile settings change
    onRsvpFontSizeChange: (Float) -> Unit, // Called when RSVP font size is adjusted
    onRsvpTextBrightnessChange: (Float) -> Unit, // Called when RSVP text brightness is adjusted
    onRsvpFontWeightChange: (RsvpFontWeight) -> Unit, // Called when RSVP font weight is adjusted
    onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit, // Called when RSVP font family is adjusted
    onThemeChange: (ReaderTheme) -> Unit, // Called when theme is changed
    onVerticalBiasChange: (Float) -> Unit, // Called when vertical position is adjusted
    onHorizontalBiasChange: (Float) -> Unit, // Called when left-bias is adjusted
    onExit: (Int) -> Unit  // Called to navigate back with resume index
) {
    val safeMinTempoMsPerWord = 30L
    val extremeMinTempoMsPerWord = 10L
    val minTempoMsPerWord = if (extremeSpeedUnlocked) extremeMinTempoMsPerWord else safeMinTempoMsPerWord
    val maxTempoMsPerWord = 240L

    var currentFontFamily by remember { mutableStateOf(fontFamily) }
    LaunchedEffect(fontFamily) {
        currentFontFamily = fontFamily
    }

    var currentTextBrightness by remember { mutableStateOf(textBrightness) }
    LaunchedEffect(textBrightness) {
        currentTextBrightness = textBrightness
    }

    // Resolve font family
    val resolvedFontFamily: FontFamily = when (currentFontFamily) {
        RsvpFontFamily.INTER -> InterFontFamily
        RsvpFontFamily.ROBOTO -> RobotoFontFamily
    }

    // Tempo adjustment state - use local state to avoid recomposition from config changes
    var currentTempoMsPerWord by remember { mutableStateOf(config.tempoMsPerWord) }
    var showTempoIndicator by remember { mutableStateOf(false) }
    var showFontSizeIndicator by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragStartTempoMsPerWord by remember { mutableStateOf(config.tempoMsPerWord) }
    var currentVerticalBias by remember { mutableStateOf(verticalBias) }
    var currentHorizontalBias by remember { mutableStateOf(horizontalBias) }
    var currentFontSizeSp by rememberSaveable { mutableFloatStateOf(fontSizeSp) }
    var currentFontWeight by remember { mutableStateOf(fontWeight) }

    LaunchedEffect(config.tempoMsPerWord) {
        currentTempoMsPerWord = config.tempoMsPerWord
    }

    // Resolve font weight (live)
    val resolvedFontWeight: FontWeight = when (currentFontWeight) {
        RsvpFontWeight.LIGHT -> FontWeight.Light
        RsvpFontWeight.NORMAL -> FontWeight.Normal
        RsvpFontWeight.MEDIUM -> FontWeight.Medium
    }

    val effectiveConfig = remember(config, currentTempoMsPerWord) {
        val derivedWpm = (60_000.0 / currentTempoMsPerWord.toDouble()).toInt().coerceAtLeast(1)
        config.copy(tempoMsPerWord = currentTempoMsPerWord, baseWpm = derivedWpm)
    }
    val frames = remember(tokens, startIndex, effectiveConfig) {
        runCatching { engine.generateFrames(tokens, startIndex, effectiveConfig) }.getOrElse { emptyList() }
    }

    var frameIndex by rememberSaveable { mutableStateOf(0) }
    var currentTokenIndex by rememberSaveable { mutableStateOf(startIndex) }
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
        showTempoIndicator = false
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

    fun addBookmarkNow() {
        if (tokens.isEmpty()) return
        val currentIndex = getCurrentTokenIndex()
        val safeIndex = tokens.nearestWordIndex(currentIndex).coerceIn(0, tokens.lastIndex)
        val preview = tokens.getOrNull(safeIndex)?.text ?: ""
        onAddBookmark(safeIndex, preview)
        showQuickSettings = false
    }

    // Save position whenever frame changes (so it's always up to date)
    LaunchedEffect(frameIndex) {
        if (frames.isNotEmpty()) {
            val currentIndex = getCurrentTokenIndex()
            currentTokenIndex = currentIndex
            val safeIndex = if (tokens.isNotEmpty()) {
                tokens.nearestWordIndex(currentIndex)
            } else {
                currentIndex
            }
            onPositionChanged(safeIndex)
        }
    }

    // Keep the current frame aligned when frames are regenerated (e.g., tempo/profile changes).
    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        val target = currentTokenIndex
        val idx = frames.indexOfLast { it.originalTokenIndex <= target }.let { if (it == -1) 0 else it }
        frameIndex = idx.coerceIn(0, frames.lastIndex)
    }

    // Reset state only when tokens or startIndex change (not when config/tempo changes)
    LaunchedEffect(tokens, startIndex) {
        frameIndex = 0
        currentTokenIndex = startIndex
        isPlaying = true
        completed = false
        // Initialize tempo from config when starting a new RSVP session
        currentTempoMsPerWord = config.tempoMsPerWord
        // Initialize RSVP font size from prefs on new session
        currentFontSizeSp = fontSizeSp
        // Initialize RSVP font weight from prefs on new session
        currentFontWeight = fontWeight
        // Initialize RSVP font family from prefs on new session
        currentFontFamily = fontFamily
        // Initialize RSVP text brightness from prefs on new session
        currentTextBrightness = textBrightness
        // Initialize vertical bias from prefs on new session
        currentVerticalBias = verticalBias
        // Initialize horizontal bias from prefs on new session
        currentHorizontalBias = horizontalBias
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

    // Auto-hide tempo indicator after a short delay
    LaunchedEffect(showTempoIndicator) {
        if (showTempoIndicator) {
            delay(1500)
            showTempoIndicator = false
        }
    }

    // Auto-hide font-size indicator after a short delay
    LaunchedEffect(showFontSizeIndicator) {
        if (showFontSizeIndicator) {
            delay(1500)
            showFontSizeIndicator = false
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

    // RSVP playback logic
    LaunchedEffect(isPlaying, frameIndex, completed, frames) {
        if (!isPlaying || completed) return@LaunchedEffect
        if (frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[frameIndex]
        delay(frame.durationMs.coerceAtLeast(16L))
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

    // Swipe threshold for tempo adjustment (pixels per 5ms change)
    val tempoSwipeThreshold = 30f

    val estimatedWpmForCurrentFrames = remember(frames) {
        val wordCount = frames.sumOf { frame -> frame.tokens.count { it.type == TokenType.WORD } }.coerceAtLeast(1)
        val totalMs = frames.sumOf { it.durationMs }.coerceAtLeast(1L)
        ((wordCount * 60_000.0) / totalMs.toDouble()).toInt().coerceAtLeast(1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Restart gesture handler when persisted tempo changes, so we don't use stale config.
            .pointerInput(config.tempoMsPerWord, isPositioningMode) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragAccumulator = 0f
                        dragStartTempoMsPerWord = currentTempoMsPerWord
                        dragStartBias = currentVerticalBias
                    },
                    onDragEnd = {
                        if (isPositioningMode) {
                            if (currentVerticalBias != dragStartBias) {
                                onVerticalBiasChange(currentVerticalBias)
                            }
                        } else {
                            // Persist the tempo change when drag ends
                            if (currentTempoMsPerWord != dragStartTempoMsPerWord) {
                                onTempoChange(currentTempoMsPerWord)
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
                            // Swipe up = faster (lower tempo). Swipe down = slower (higher tempo).
                            val tempoDeltaMs = (dragAccumulator / tempoSwipeThreshold).toInt() * 5L
                            if (tempoDeltaMs != 0L) {
                                val newTempo =
                                    (dragStartTempoMsPerWord + tempoDeltaMs).coerceIn(minTempoMsPerWord, maxTempoMsPerWord)
                                if (newTempo != currentTempoMsPerWord) {
                                    currentTempoMsPerWord = newTempo
                                    showTempoIndicator = true
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
                    } else if (showQuickSettings) {
                        // Don't accidentally pause/resume while the user is adjusting settings.
                        showQuickSettings = false
                    } else {
                        // Toggle playback: tap to pause, tap again to resume.
                        if (!completed) {
                            isPlaying = !isPlaying
                            showTempoIndicator = false
                            showFontSizeIndicator = false
                            showControls = !isPlaying
                        }
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
        // Subtle line color - less vibrant than the pivot character
        val pivotLineColor = MaterialTheme.colorScheme.onBackground.copy(
            alpha = (0.15f * currentTextBrightness.coerceIn(0.55f, 1.0f)).coerceIn(0f, 1f)
        )
        val rsvpTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = currentTextBrightness.coerceIn(0.55f, 1.0f))

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
                        textColor = rsvpTextColor,
                        pivotLineColor = pivotLineColor,
                        fontSizeSp = currentFontSizeSp,
                        fontFamily = resolvedFontFamily,
                        fontWeight = resolvedFontWeight,
                        horizontalBias = currentHorizontalBias
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
                .navigationBarsPadding()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )

        // Top bar with close and settings buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
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

        // Pace indicator - shows when adjusting via swipe
        AnimatedVisibility(
            visible = showTempoIndicator,
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
                    text = "~$estimatedWpmForCurrentFrames WPM",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Font size indicator - shows when adjusting via quick settings
        AnimatedVisibility(
            visible = showFontSizeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 112.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${currentFontSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    .fillMaxHeight(0.85f)
                    .navigationBarsPadding()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var showRsvpSettings by remember { mutableStateOf(false) }

                if (!showRsvpSettings) {
                    Text(
                        "Quick Settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SettingsNavRow(
                        title = "Bookmarks",
                        subtitle = "Open saved bookmarks",
                        icon = Icons.Default.Bookmark,
                        onClick = {
                            showQuickSettings = false
                            onOpenBookmarks()
                        }
                    )
                    SettingsNavRow(
                        title = "Add bookmark",
                        subtitle = "Save this position",
                        icon = Icons.Default.Bookmark,
                        showChevron = false,
                        onClick = { addBookmarkNow() }
                    )

                    SettingsNavRow(
                        title = "RSVP settings",
                        subtitle = "Timing profile, readability, display",
                        icon = Icons.Default.Settings,
                        onClick = { showRsvpSettings = true }
                    )

                    ThemeSelector(selected = readerTheme, onThemeChange = onThemeChange)

                    SettingsSwitchRow(
                        title = "Focus mode",
                        subtitle = "Hide system chrome while reading.",
                        checked = focusModeEnabled,
                        onCheckedChange = onFocusModeEnabledChange
                    )

                    SettingsSwitchRow(
                        title = "Positioning mode",
                        subtitle = "Swipe to adjust text position.",
                        checked = isPositioningMode,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                enterPositioningMode()
                            } else {
                                finishPositioning(resumeIfWasPlaying = false)
                            }
                        }
                    )

                    Text(
                        "~$estimatedWpmForCurrentFrames WPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsSliderRow(
                        title = "Tempo",
                        subtitle = "Lower = faster.",
                        valueLabel = "${currentTempoMsPerWord}ms",
                        value = currentTempoMsPerWord.toFloat(),
                        onValueChange = { newValue ->
                            currentTempoMsPerWord = newValue.toLong().coerceIn(minTempoMsPerWord, maxTempoMsPerWord)
                        },
                        onValueChangeFinished = { onTempoChange(currentTempoMsPerWord) },
                        valueRange = minTempoMsPerWord.toFloat()..maxTempoMsPerWord.toFloat()
                    )

                    SettingsSwitchRow(
                        title = "Unlock extreme speeds",
                        subtitle = "Allows down to ${extremeMinTempoMsPerWord}ms (can become unreadable).",
                        checked = extremeSpeedUnlocked,
                        onCheckedChange = { enabled ->
                            onExtremeSpeedUnlockedChange(enabled)
                            if (!enabled && currentTempoMsPerWord < safeMinTempoMsPerWord) {
                                currentTempoMsPerWord = safeMinTempoMsPerWord
                                onTempoChange(currentTempoMsPerWord)
                            }
                        }
                    )

                    SettingsSliderRow(
                        title = "Text size",
                        valueLabel = "${currentFontSizeSp.toInt()}sp",
                        value = currentFontSizeSp,
                        onValueChange = { newValue ->
                            currentFontSizeSp = newValue.coerceIn(28f, 80f)
                            showFontSizeIndicator = true
                        },
                        onValueChangeFinished = { onRsvpFontSizeChange(currentFontSizeSp) },
                        valueRange = 28f..80f
                    )

                    Text(
                        "Swipe up/down to adjust speed\nUse sliders to preview changes\nLong press to exit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SettingsNavRow(
                        title = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        showChevron = false,
                        onClick = { showRsvpSettings = false }
                    )
                    Text(
                        "RSVP settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val configForSettings = remember(config, currentTempoMsPerWord) {
                        config.copy(tempoMsPerWord = currentTempoMsPerWord)
                    }
                    RsvpSettingsContent(
                        selectedProfileId = selectedProfileId,
                        customProfiles = customProfiles,
                        config = configForSettings,
                        unlockExtremeSpeed = extremeSpeedUnlocked,
                        rsvpFontSizeSp = currentFontSizeSp,
                        rsvpTextBrightness = currentTextBrightness,
                        rsvpFontFamily = currentFontFamily,
                        rsvpFontWeight = currentFontWeight,
                        rsvpVerticalBias = currentVerticalBias,
                        rsvpHorizontalBias = currentHorizontalBias,
                        onSelectProfile = onSelectProfile,
                        onSaveCustomProfile = onSaveCustomProfile,
                        onDeleteCustomProfile = onDeleteCustomProfile,
                        onConfigChange = { updated ->
                            currentTempoMsPerWord = updated.tempoMsPerWord
                            onRsvpConfigChange(updated)
                        },
                        onUnlockExtremeSpeedChange = onExtremeSpeedUnlockedChange,
                        onRsvpFontSizeChange = { size ->
                            currentFontSizeSp = size
                            showFontSizeIndicator = true
                            onRsvpFontSizeChange(size)
                        },
                        onRsvpTextBrightnessChange = { brightness ->
                            currentTextBrightness = brightness
                            onRsvpTextBrightnessChange(brightness)
                        },
                        onRsvpFontWeightChange = { weight ->
                            currentFontWeight = weight
                            onRsvpFontWeightChange(weight)
                        },
                        onRsvpFontFamilyChange = { family ->
                            currentFontFamily = family
                            onRsvpFontFamilyChange(family)
                        },
                        onRsvpVerticalBiasChange = { bias ->
                            currentVerticalBias = bias
                            onVerticalBiasChange(bias)
                        },
                        onRsvpHorizontalBiasChange = { bias ->
                            currentHorizontalBias = bias
                            onHorizontalBiasChange(bias)
                        }
                    )
                }
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
                    .navigationBarsPadding()
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
    pivotLineColor: Color,
    fontSizeSp: Float = 44f,
    fontFamily: FontFamily = InterFontFamily,
    fontWeight: FontWeight = FontWeight.Light,
    horizontalBias: Float = -0.12f
) {
    // Build the display string with sections
    val firstWord = tokens.firstOrNull { it.type == TokenType.WORD }
    val orpIndex = firstWord?.orpIndex ?: 0

    // Build full text with punctuation attached correctly
    // Opening punctuation attaches to the next word (no space after)
    // Closing punctuation attaches to the previous word (no space before)
    val openingPunctuation = setOf('(', '[', '{', '\u201C', '\u2018')

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
                    val ch = token.text.singleOrNull()
                    val isOpening = when {
                        ch == null -> false
                        ch == '"' -> !needsSpace
                        ch in openingPunctuation -> true
                        else -> false
                    }
                    // No space before closing punctuation
                    // Space before opening punctuation (if not at start)
                    if (isOpening && needsSpace) append(" ")
                    append(token.text)
                    // Opening punctuation: no space after (next word attaches directly)
                    // Closing punctuation: space after (next word is separate)
                    needsSpace = !isOpening
                }
                TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> { /* ignore */ }
            }
        }
    }

    // Calculate the pivot position in the full string (constrained to the first WORD token).
    val wordStart = if (firstWord != null) fullText.indexOf(firstWord.text) else -1
    val wordEndExclusive = if (firstWord != null && wordStart >= 0) wordStart + firstWord.text.length else -1
    val pivotPosition = if (firstWord != null && wordStart >= 0) {
        (wordStart + orpIndex).coerceIn(wordStart, (wordEndExclusive - 1).coerceAtLeast(wordStart))
    } else {
        0
    }

    // Use fixed font size for consistent visual experience
    // Long words will naturally fit due to the ORP centering and padding
    val textStyle = MaterialTheme.typography.displayMedium.copy(
        fontFamily = fontFamily,
        fontSize = fontSizeSp.sp,
        fontWeight = fontWeight,
        letterSpacing = 0.5.sp
    )

    val clampedBias = horizontalBias.coerceIn(-0.6f, 0.6f)
    val density = LocalDensity.current
    val safePivotIndex = remember(fullText, pivotPosition) {
        if (fullText.isEmpty()) 0 else pivotPosition.coerceIn(0, fullText.lastIndex)
    }
    val baseAnnotatedText = remember(fullText) { buildAnnotatedString { append(fullText) } }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val rawFraction = ((clampedBias + 1f) / 2f).coerceIn(0.05f, 0.95f)
        val baseEdgePx = with(density) { 24.dp.toPx() }
        val extraEdgePx = with(density) { 14.dp.toPx() }
        val biasFromCenter = rawFraction - 0.5f
        val leftExtraFactor = (biasFromCenter / 0.5f).coerceAtLeast(0f).coerceIn(0f, 1f)
        val rightExtraFactor = (-biasFromCenter / 0.5f).coerceAtLeast(0f).coerceIn(0f, 1f)
        val safeLeftPx = baseEdgePx + (leftExtraFactor * extraEdgePx)
        val safeRightPx = baseEdgePx + (rightExtraFactor * extraEdgePx)

        val minFraction = (safeLeftPx / maxWidthPx).coerceIn(0.05f, 0.45f)
        val maxFraction = 1f - (safeRightPx / maxWidthPx).coerceIn(0.05f, 0.45f)
        val desiredFraction = rawFraction.coerceIn(minFraction, maxFraction)
        val desiredPivotX = (maxWidthPx * desiredFraction).coerceIn(safeLeftPx, maxWidthPx - safeRightPx)
        val guideBias = ((desiredPivotX / maxWidthPx) * 2f) - 1f

        val measured = remember(baseAnnotatedText, textStyle) {
            textMeasurer.measure(
                text = baseAnnotatedText,
                style = textStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = Int.MAX_VALUE)
            )
        }
        val measuredWidthPx = measured.size.width.toFloat()

        val minTranslationX = safeLeftPx
        val maxTranslationX = maxWidthPx - safeRightPx - measuredWidthPx

        val pivotCandidateStart = if (wordStart >= 0) wordStart else 0
        val pivotCandidateEnd = if (wordEndExclusive > 0) wordEndExclusive - 1 else (fullText.lastIndex).coerceAtLeast(0)

        fun pivotCenterX(index: Int): Float {
            val safeIndex = index.coerceIn(0, (fullText.lastIndex).coerceAtLeast(0))
            val box = measured.getBoundingBox(safeIndex)
            return box.left + (box.width / 2f)
        }

        val pivotIndex: Int
        val translationX: Float
        if (maxTranslationX < minTranslationX) {
            // Word is wider than the safe area; center it to reduce perceived edge-hugging.
            pivotIndex = safePivotIndex.coerceIn(pivotCandidateStart, pivotCandidateEnd)
            translationX = (maxWidthPx - measuredWidthPx) / 2f
        } else {
            // Choose a pivot index within the word that maximizes "comfort" margins
            // while keeping the pivot as close as possible to the computed ORP.
            val basePivotIndex = safePivotIndex.coerceIn(pivotCandidateStart, pivotCandidateEnd)
            var bestIndex = basePivotIndex
            var bestClampDelta = Float.POSITIVE_INFINITY
            var bestMinExtra = Float.NEGATIVE_INFINITY
            var bestPivotPenalty = Int.MAX_VALUE

            for (i in pivotCandidateStart..pivotCandidateEnd) {
                val rawTranslation = desiredPivotX - pivotCenterX(i)
                val clampedTranslation = rawTranslation.coerceIn(minTranslationX, maxTranslationX)
                val clampDelta = kotlin.math.abs(clampedTranslation - rawTranslation)

                val leftMargin = clampedTranslation
                val rightMargin = maxWidthPx - (clampedTranslation + measuredWidthPx)
                val minExtra = kotlin.math.min(leftMargin - safeLeftPx, rightMargin - safeRightPx)
                val pivotPenalty = kotlin.math.abs(i - basePivotIndex)

                val isBetter = when {
                    clampDelta < bestClampDelta - 0.5f -> true
                    kotlin.math.abs(clampDelta - bestClampDelta) <= 0.5f && minExtra > bestMinExtra + 0.5f -> true
                    kotlin.math.abs(clampDelta - bestClampDelta) <= 0.5f &&
                        kotlin.math.abs(minExtra - bestMinExtra) <= 0.5f &&
                        pivotPenalty < bestPivotPenalty -> true
                    else -> false
                }

                if (isBetter) {
                    bestIndex = i
                    bestClampDelta = clampDelta
                    bestMinExtra = minExtra
                    bestPivotPenalty = pivotPenalty
                }
            }

            pivotIndex = bestIndex
            translationX = (desiredPivotX - pivotCenterX(pivotIndex)).coerceIn(minTranslationX, maxTranslationX)
        }

        val annotatedText = remember(fullText, pivotIndex, pivotColor) {
            buildAnnotatedString {
                append(fullText)
                if (fullText.isNotEmpty()) {
                    val safeIndex = pivotIndex.coerceIn(0, fullText.lastIndex)
                    addStyle(
                        style = SpanStyle(color = pivotColor),
                        start = safeIndex,
                        end = (safeIndex + 1).coerceAtMost(fullText.length)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
        // Top static line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(pivotLineColor)
        )

        // Vertical pointer down (biased)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = guideBias, verticalBias = 0f))
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(pivotLineColor)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

            // ORP-aligned text rendered as a single Text for correct kerning/spacing.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Text(
                    text = annotatedText,
                    style = textStyle,
                    color = textColor,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            this.translationX = translationX
                        }
                )
            }

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical pointer up (biased)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = guideBias, verticalBias = 0f))
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(pivotLineColor)
            )
        }

        // Bottom static line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(pivotLineColor)
        )
        }
    }
}
