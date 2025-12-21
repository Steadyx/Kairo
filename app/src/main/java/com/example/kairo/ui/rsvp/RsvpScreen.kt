@file:Suppress("FunctionNaming")

package com.example.kairo.ui.rsvp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.ReaderTheme
import com.example.kairo.core.model.RsvpConfig
import com.example.kairo.core.model.RsvpCustomProfile
import com.example.kairo.core.model.RsvpFontFamily
import com.example.kairo.core.model.RsvpFontWeight
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.data.rsvp.RsvpFrameRepository
import com.example.kairo.data.rsvp.RsvpFrameSet
import com.example.kairo.ui.settings.RsvpSettingsContent
import com.example.kairo.ui.settings.SettingsNavRow
import com.example.kairo.ui.settings.SettingsSliderRow
import com.example.kairo.ui.settings.SettingsSwitchRow
import com.example.kairo.ui.settings.ThemeSelector
import android.os.SystemClock
import com.example.kairo.ui.theme.InterFontFamily
import com.example.kairo.ui.theme.RobotoFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class RsvpScreenState(
    val book: RsvpBookContext,
    val profile: RsvpProfileContext,
    val uiPrefs: RsvpUiPreferences,
    val textStyle: RsvpTextStyle,
    val layoutBias: RsvpLayoutBias
)

data class RsvpBookContext(
    val bookId: BookId,
    val chapterIndex: Int,
    val tokens: List<Token>,
    val startIndex: Int
)

data class RsvpProfileContext(
    val config: RsvpConfig,
    val selectedProfileId: String,
    val customProfiles: List<RsvpCustomProfile>
)

data class RsvpUiPreferences(
    val extremeSpeedUnlocked: Boolean,
    val readerTheme: ReaderTheme,
    val focusModeEnabled: Boolean
)

data class RsvpTextStyle(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val fontFamily: RsvpFontFamily = DEFAULT_FONT_FAMILY,
    val fontWeight: RsvpFontWeight = DEFAULT_FONT_WEIGHT,
    val textBrightness: Float = DEFAULT_TEXT_BRIGHTNESS
)

data class RsvpLayoutBias(
    val verticalBias: Float = DEFAULT_VERTICAL_BIAS,
    val horizontalBias: Float = DEFAULT_HORIZONTAL_BIAS
)

data class RsvpScreenCallbacks(
    val bookmarks: RsvpBookmarkCallbacks,
    val playback: RsvpPlaybackCallbacks,
    val preferences: RsvpPreferenceCallbacks,
    val ui: RsvpUiCallbacks,
    val theme: RsvpThemeCallbacks
)

data class RsvpBookmarkCallbacks(
    val onAddBookmark: (tokenIndex: Int, previewText: String) -> Unit,
    val onOpenBookmarks: () -> Unit
)

data class RsvpPlaybackCallbacks(
    val onFinished: (Int) -> Unit,
    val onPositionChanged: (Int) -> Unit,
    val onTempoChange: (Long) -> Unit,
    val onExit: (Int) -> Unit
)

data class RsvpPreferenceCallbacks(
    val onExtremeSpeedUnlockedChange: (Boolean) -> Unit,
    val onSelectProfile: (String) -> Unit,
    val onSaveCustomProfile: (String, RsvpConfig) -> Unit,
    val onDeleteCustomProfile: (String) -> Unit,
    val onRsvpConfigChange: (RsvpConfig) -> Unit
)

data class RsvpUiCallbacks(
    val onFocusModeEnabledChange: (Boolean) -> Unit,
    val onRsvpFontSizeChange: (Float) -> Unit,
    val onRsvpTextBrightnessChange: (Float) -> Unit,
    val onRsvpFontWeightChange: (RsvpFontWeight) -> Unit,
    val onRsvpFontFamilyChange: (RsvpFontFamily) -> Unit
)

data class RsvpThemeCallbacks(
    val onThemeChange: (ReaderTheme) -> Unit,
    val onVerticalBiasChange: (Float) -> Unit,
    val onHorizontalBiasChange: (Float) -> Unit
)

data class RsvpScreenDependencies(
    val frameRepository: RsvpFrameRepository
)

private data class OrpTypography(
    val fontSizeSp: Float,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight
)

private data class OrpColors(
    val textColor: Color,
    val pivotColor: Color,
    val pivotLineColor: Color
)

private data class OrpTextLayout(
    val horizontalBias: Float,
    val lockPivot: Boolean
)

private data class RsvpFrameLoadState(
    val frames: List<com.example.kairo.core.model.RsvpFrame>,
    val baseTempoMs: Long,
    val isLoading: Boolean
)

private data class RsvpTimingInfo(
    val minTempoMs: Long,
    val maxTempoMs: Long,
    val tempoScale: Double
)

private data class RsvpUiContext(
    val state: RsvpScreenState,
    val callbacks: RsvpScreenCallbacks,
    val runtime: RsvpRuntimeState,
    val frameState: RsvpFrameLoadState,
    val timing: RsvpTimingInfo
)

private class RsvpRuntimeState {
    var currentTempoMsPerWord by mutableLongStateOf(0L)
    var showTempoIndicator by mutableStateOf(false)
    var showFontSizeIndicator by mutableStateOf(false)
    var dragAccumulator by mutableFloatStateOf(ZERO_FLOAT)
    var dragStartTempoMsPerWord by mutableLongStateOf(0L)
    var currentVerticalBias by mutableFloatStateOf(ZERO_FLOAT)
    var currentHorizontalBias by mutableFloatStateOf(ZERO_FLOAT)
    var currentFontSizeSp by mutableFloatStateOf(ZERO_FLOAT)
    var currentFontWeight by mutableStateOf(DEFAULT_FONT_WEIGHT)
    var currentFontFamily by mutableStateOf(DEFAULT_FONT_FAMILY)
    var currentTextBrightness by mutableFloatStateOf(DEFAULT_TEXT_BRIGHTNESS)
    var frameIndex by mutableIntStateOf(0)
    var currentTokenIndex by mutableIntStateOf(0)
    var isPlaying by mutableStateOf(true)
    var completed by mutableStateOf(false)
    var showControls by mutableStateOf(false)
    var showQuickSettings by mutableStateOf(false)
    var isAdjustingPosition by mutableStateOf(false)
    var isPositioningMode by mutableStateOf(false)
    var dragStartBias by mutableFloatStateOf(ZERO_FLOAT)
    var dragStartHorizontalBias by mutableFloatStateOf(ZERO_FLOAT)
    var wasPlayingBeforePositioning by mutableStateOf(true)
    var lastPositionSaveMs by mutableLongStateOf(0L)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RsvpScreen(
    state: RsvpScreenState,
    callbacks: RsvpScreenCallbacks,
    dependencies: RsvpScreenDependencies
) {
    val minTempoMsPerWord = if (state.uiPrefs.extremeSpeedUnlocked) {
        EXTREME_MIN_TEMPO_MS_PER_WORD
    } else {
        SAFE_MIN_TEMPO_MS_PER_WORD
    }
    val runtime = rememberRsvpRuntimeState(
        profile = state.profile,
        textStyle = state.textStyle,
        layoutBias = state.layoutBias,
        startIndex = state.book.startIndex
    )
    val frameState = rememberFrameLoadState(
        book = state.book,
        profile = state.profile,
        frameRepository = dependencies.frameRepository
    )
    val tempoScale = rememberTempoScale(
        currentTempoMsPerWord = runtime.currentTempoMsPerWord,
        baseTempoMs = frameState.baseTempoMs
    )
    val timing = RsvpTimingInfo(
        minTempoMs = minTempoMsPerWord,
        maxTempoMs = MAX_TEMPO_MS_PER_WORD,
        tempoScale = tempoScale
    )
    val context = RsvpUiContext(
        state = state,
        callbacks = callbacks,
        runtime = runtime,
        frameState = frameState,
        timing = timing
    )

    RsvpBackHandler(context)
    RsvpPlaybackEffects(context)
    RsvpIndicatorEffects(runtime)

    if (shouldShowLoading(frameState)) {
        RsvpLoadingState()
        return
    }
    if (frameState.frames.isEmpty()) {
        RsvpEmptyState { exitAndSavePosition(context) }
        return
    }

    RsvpPlaybackSurface(context)
}

@Composable
private fun rememberRsvpRuntimeState(
    profile: RsvpProfileContext,
    textStyle: RsvpTextStyle,
    layoutBias: RsvpLayoutBias,
    startIndex: Int
): RsvpRuntimeState {
    val state = remember {
        RsvpRuntimeState().apply {
            currentTempoMsPerWord = profile.config.tempoMsPerWord
            dragStartTempoMsPerWord = profile.config.tempoMsPerWord
            currentFontFamily = textStyle.fontFamily
            currentTextBrightness = textStyle.textBrightness
            currentFontSizeSp = textStyle.fontSizeSp
            currentFontWeight = textStyle.fontWeight
            currentVerticalBias = layoutBias.verticalBias
            currentHorizontalBias = layoutBias.horizontalBias
            dragStartBias = layoutBias.verticalBias
            dragStartHorizontalBias = layoutBias.horizontalBias
            currentTokenIndex = startIndex
        }
    }

    LaunchedEffect(profile.config.tempoMsPerWord) {
        state.currentTempoMsPerWord = profile.config.tempoMsPerWord
    }
    LaunchedEffect(textStyle.fontFamily) {
        state.currentFontFamily = textStyle.fontFamily
    }
    LaunchedEffect(textStyle.textBrightness) {
        state.currentTextBrightness = textStyle.textBrightness
    }

    return state
}

@Composable
private fun rememberFrameLoadState(
    book: RsvpBookContext,
    profile: RsvpProfileContext,
    frameRepository: RsvpFrameRepository
): RsvpFrameLoadState {
    var frameSet by remember { mutableStateOf<RsvpFrameSet?>(null) }
    var isFramesLoading by remember { mutableStateOf(true) }

    LaunchedEffect(book.bookId, book.chapterIndex, profile.config) {
        val hadFrames = frameSet?.frames?.isNotEmpty() == true
        if (!hadFrames) isFramesLoading = true

        val computed = runCatching {
            frameRepository.getFrames(book.bookId, book.chapterIndex, profile.config)
        }.getOrNull()

        frameSet = computed
        isFramesLoading = false
    }

    val frames = frameSet?.frames.orEmpty()
    val baseTempoMs = frameSet?.baseTempoMs ?: profile.config.tempoMsPerWord
    return RsvpFrameLoadState(
        frames = frames,
        baseTempoMs = baseTempoMs,
        isLoading = isFramesLoading
    )
}

@Composable
private fun rememberTempoScale(currentTempoMsPerWord: Long, baseTempoMs: Long): Double {
    return remember(currentTempoMsPerWord, baseTempoMs) {
        if (baseTempoMs <= 0L) {
            DEFAULT_TEMPO_SCALE
        } else {
            (currentTempoMsPerWord.toDouble() / baseTempoMs.toDouble())
                .coerceIn(TEMPO_SCALE_MIN, TEMPO_SCALE_MAX)
        }
    }
}

private fun shouldShowLoading(frameState: RsvpFrameLoadState): Boolean {
    return frameState.isLoading && frameState.frames.isEmpty()
}

@Composable
private fun RsvpBackHandler(context: RsvpUiContext) {
    BackHandler { exitAndSavePosition(context) }
}

@Composable
private fun RsvpPlaybackEffects(context: RsvpUiContext) {
    RsvpPositionSaveEffect(context)
    RsvpFrameAlignmentEffect(context)
    RsvpSessionResetEffect(context)
    RsvpPlaybackLoopEffect(context)
}

@Composable
private fun RsvpIndicatorEffects(runtime: RsvpRuntimeState) {
    RsvpAutoHideControlsEffect(runtime)
    RsvpAutoHideTempoIndicatorEffect(runtime)
    RsvpAutoHideFontSizeIndicatorEffect(runtime)
}

@Composable
private fun RsvpPositionSaveEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    LaunchedEffect(runtime.frameIndex) {
        if (frames.isEmpty()) return@LaunchedEffect
        val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
        runtime.currentTokenIndex = currentIndex
        val safeIndex = if (book.tokens.isNotEmpty()) {
            book.tokens.nearestWordIndex(currentIndex)
        } else {
            currentIndex
        }
        val now = SystemClock.elapsedRealtime()
        val shouldSave = now - runtime.lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS ||
            runtime.frameIndex == frames.lastIndex
        if (shouldSave) {
            runtime.lastPositionSaveMs = now
            context.callbacks.playback.onPositionChanged(safeIndex)
        }
    }
}

@Composable
private fun RsvpFrameAlignmentEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames

    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        runtime.frameIndex = alignFrameIndex(frames, runtime.currentTokenIndex)
    }
}

@Composable
private fun RsvpSessionResetEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val book = context.state.book
    val profile = context.state.profile
    val textStyle = context.state.textStyle
    val layoutBias = context.state.layoutBias
    val frames = context.frameState.frames

    LaunchedEffect(book.tokens, book.startIndex) {
        runtime.currentTokenIndex = book.startIndex
        runtime.frameIndex = alignFrameIndex(frames, book.startIndex)
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
        runtime.showQuickSettings = false
        runtime.showControls = false
    }
}

@Composable
private fun RsvpPlaybackLoopEffect(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val tokens = context.state.book.tokens
    val tempoScale = context.timing.tempoScale

    LaunchedEffect(runtime.isPlaying, runtime.frameIndex, runtime.completed, frames, tempoScale) {
        if (!runtime.isPlaying || runtime.completed) return@LaunchedEffect
        if (runtime.frameIndex >= frames.size) return@LaunchedEffect
        val frame = frames[runtime.frameIndex]
        val scaledMs = (frame.durationMs * tempoScale)
            .roundToLong()
            .coerceAtLeast(MIN_FRAME_DELAY_MS)
        delay(scaledMs)
        if (runtime.frameIndex == frames.lastIndex) {
            runtime.completed = true
            val rawNextIndex = frame.originalTokenIndex + 1
            val safeNextIndex = if (tokens.isNotEmpty()) {
                tokens.nearestWordIndex(rawNextIndex)
            } else {
                rawNextIndex
            }
            context.callbacks.playback.onFinished(safeNextIndex)
        } else {
            runtime.frameIndex += 1
        }
    }
}

@Composable
private fun RsvpAutoHideControlsEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showControls, runtime.isPlaying) {
        if (runtime.showControls && runtime.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            runtime.showControls = false
        }
    }
}

@Composable
private fun RsvpAutoHideTempoIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showTempoIndicator) {
        if (runtime.showTempoIndicator) {
            delay(TEMPO_INDICATOR_HIDE_DELAY_MS)
            runtime.showTempoIndicator = false
        }
    }
}

@Composable
private fun RsvpAutoHideFontSizeIndicatorEffect(runtime: RsvpRuntimeState) {
    LaunchedEffect(runtime.showFontSizeIndicator) {
        if (runtime.showFontSizeIndicator) {
            delay(FONT_SIZE_INDICATOR_HIDE_DELAY_MS)
            runtime.showFontSizeIndicator = false
        }
    }
}

@Composable
private fun RsvpLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Preparing RSVP...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = LOADING_TEXT_ALPHA)
        )
    }
}

@Composable
private fun RsvpEmptyState(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onExit() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No content to display.\nTap to go back.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = LOADING_TEXT_ALPHA)
        )
    }
}

@Composable
private fun RsvpPlaybackSurface(context: RsvpUiContext) {
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

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.rsvpGestureModifier(
    context: RsvpUiContext,
    interactionSource: MutableInteractionSource
): Modifier {
    val runtime = context.runtime
    return this
        .pointerInput(context.state.profile.config.tempoMsPerWord, runtime.isPositioningMode) {
            detectDragGestures(
                onDragStart = { handleDragStart(runtime) },
                onDragEnd = { handleDragEnd(context) },
                onDrag = { change, dragAmount ->
                    handleDrag(context, dragAmount)
                    change.consume()
                }
            )
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { handleTap(context) },
            onLongClick = { exitAndSavePosition(context) }
        )
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

@Composable
private fun BoxScope.RsvpProgressBar(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val progress = (runtime.frameIndex + 1).toFloat() / frames.size.toFloat()

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .height(PROGRESS_HEIGHT),
        color = MaterialTheme.colorScheme.primary.copy(alpha = PROGRESS_PRIMARY_ALPHA),
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PROGRESS_TRACK_ALPHA)
    )
}

@Composable
private fun BoxScope.RsvpTopBar(context: RsvpUiContext) {
    val runtime = context.runtime

    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(TOP_BAR_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TOP_BAR_SPACING)
    ) {
        IconButton(onClick = {
            if (runtime.isPositioningMode) {
                finishPositioning(context, resumeIfWasPlaying = true)
            } else {
                runtime.showQuickSettings = !runtime.showQuickSettings
                if (runtime.showQuickSettings) runtime.showControls = false
            }
        }) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = TOP_BAR_ICON_ALPHA),
                modifier = Modifier.size(TOP_BAR_ICON_SIZE)
            )
        }
        IconButton(onClick = { exitAndSavePosition(context) }) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = TOP_BAR_ICON_ALPHA),
                modifier = Modifier.size(TOP_BAR_ICON_SIZE)
            )
        }
    }
}

@Composable
private fun BoxScope.RsvpTempoIndicator(context: RsvpUiContext, estimatedWpm: Int) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showTempoIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box(
            modifier = Modifier
                .padding(top = TEMPO_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS)
                )
                .padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL
                )
        ) {
            Text(
                text = "~$estimatedWpm WPM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BoxScope.RsvpFontSizeIndicator(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showFontSizeIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box(
            modifier = Modifier
                .padding(top = FONT_SIZE_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS)
                )
                .padding(
                    horizontal = INDICATOR_PADDING_HORIZONTAL,
                    vertical = INDICATOR_PADDING_VERTICAL
                )
        ) {
            Text(
                text = "${runtime.currentFontSizeSp.toInt()}sp",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BoxScope.RsvpPositioningIndicator(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.isPositioningMode,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box(
            modifier = Modifier
                .padding(top = TEMPO_INDICATOR_TOP_PADDING)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = INDICATOR_BACKGROUND_ALPHA
                    ),
                    RoundedCornerShape(INDICATOR_CORNER_RADIUS)
                )
                .clickable { finishPositioning(context, resumeIfWasPlaying = true) }
                .padding(
                    horizontal = POSITIONING_INDICATOR_PADDING_HORIZONTAL,
                    vertical = POSITIONING_INDICATOR_PADDING_VERTICAL
                )
        ) {
            Text(
                text = "Positioning mode - swipe to move\nTap here to finish",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BoxScope.RsvpQuickSettingsPanel(context: RsvpUiContext, estimatedWpm: Int) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showQuickSettings,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(QUICK_SETTINGS_HEIGHT_FRACTION)
                .navigationBarsPadding()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = QUICK_SETTINGS_BACKGROUND_ALPHA
                    ),
                    RoundedCornerShape(
                        topStart = QUICK_SETTINGS_CORNER,
                        topEnd = QUICK_SETTINGS_CORNER
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = QUICK_SETTINGS_HORIZONTAL_PADDING,
                    vertical = QUICK_SETTINGS_VERTICAL_PADDING
                ),
            verticalArrangement = Arrangement.spacedBy(QUICK_SETTINGS_SPACING)
        ) {
            var showRsvpSettings by remember { mutableStateOf(false) }
            if (showRsvpSettings) {
                RsvpQuickSettingsAdvanced(context) { showRsvpSettings = false }
            } else {
                RsvpQuickSettingsMain(
                    context = context,
                    estimatedWpm = estimatedWpm,
                    onOpenRsvpSettings = { showRsvpSettings = true }
                )
            }
        }
    }
}

@Composable
private fun RsvpQuickSettingsMain(
    context: RsvpUiContext,
    estimatedWpm: Int,
    onOpenRsvpSettings: () -> Unit
) {
    RsvpQuickSettingsHeader()
    RsvpQuickSettingsBookmarks(context, onOpenRsvpSettings)
    RsvpQuickSettingsThemeAndFocus(context)
    RsvpQuickSettingsPositioningToggle(context)
    RsvpQuickSettingsTempoControls(context, estimatedWpm)
    RsvpQuickSettingsTextSizeControls(context)
    RsvpQuickSettingsHints()
}

@Composable
private fun RsvpQuickSettingsHeader() {
    Text(
        "Quick Settings",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RsvpQuickSettingsBookmarks(
    context: RsvpUiContext,
    onOpenRsvpSettings: () -> Unit
) {
    val runtime = context.runtime

    SettingsNavRow(
        title = "Bookmarks",
        subtitle = "Open saved bookmarks",
        icon = Icons.Default.Bookmark,
        onClick = {
            runtime.showQuickSettings = false
            context.callbacks.bookmarks.onOpenBookmarks()
        }
    )
    SettingsNavRow(
        title = "Add bookmark",
        subtitle = "Save this position",
        icon = Icons.Default.Bookmark,
        showChevron = false,
        onClick = { addBookmarkNow(context) }
    )
    SettingsNavRow(
        title = "RSVP settings",
        subtitle = "Timing profile, readability, display",
        icon = Icons.Default.Settings,
        onClick = onOpenRsvpSettings
    )
}

@Composable
private fun RsvpQuickSettingsThemeAndFocus(context: RsvpUiContext) {
    ThemeSelector(
        selected = context.state.uiPrefs.readerTheme,
        onThemeChange = context.callbacks.theme.onThemeChange
    )
    SettingsSwitchRow(
        title = "Focus mode",
        subtitle = "Hide system chrome while reading.",
        checked = context.state.uiPrefs.focusModeEnabled,
        onCheckedChange = context.callbacks.ui.onFocusModeEnabledChange
    )
}

@Composable
private fun RsvpQuickSettingsPositioningToggle(context: RsvpUiContext) {
    val runtime = context.runtime

    SettingsSwitchRow(
        title = "Positioning mode",
        subtitle = "Swipe to adjust text position.",
        checked = runtime.isPositioningMode,
        onCheckedChange = { enabled ->
            if (enabled) {
                enterPositioningMode(runtime)
            } else {
                finishPositioning(context, resumeIfWasPlaying = false)
            }
        }
    )
}

@Composable
private fun RsvpQuickSettingsTempoControls(context: RsvpUiContext, estimatedWpm: Int) {
    val runtime = context.runtime
    val minTempoMs = context.timing.minTempoMs
    val maxTempoMs = context.timing.maxTempoMs

    Text(
        "~$estimatedWpm WPM",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SettingsSliderRow(
        title = "Tempo",
        subtitle = "Lower = faster.",
        valueLabel = "${runtime.currentTempoMsPerWord}ms",
        value = runtime.currentTempoMsPerWord.toFloat(),
        onValueChange = { newValue ->
            runtime.currentTempoMsPerWord = newValue.toLong().coerceIn(minTempoMs, maxTempoMs)
        },
        onValueChangeFinished = {
            context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
        },
        valueRange = minTempoMs.toFloat()..maxTempoMs.toFloat()
    )
    SettingsSwitchRow(
        title = "Unlock extreme speeds",
        subtitle = "Allows down to ${EXTREME_MIN_TEMPO_MS_PER_WORD}ms (can become unreadable).",
        checked = context.state.uiPrefs.extremeSpeedUnlocked,
        onCheckedChange = { enabled ->
            context.callbacks.preferences.onExtremeSpeedUnlockedChange(enabled)
            if (!enabled && runtime.currentTempoMsPerWord < SAFE_MIN_TEMPO_MS_PER_WORD) {
                runtime.currentTempoMsPerWord = SAFE_MIN_TEMPO_MS_PER_WORD
                context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
            }
        }
    )
}

@Composable
private fun RsvpQuickSettingsTextSizeControls(context: RsvpUiContext) {
    val runtime = context.runtime

    SettingsSliderRow(
        title = "Text size",
        valueLabel = "${runtime.currentFontSizeSp.toInt()}sp",
        value = runtime.currentFontSizeSp,
        onValueChange = { newValue ->
            runtime.currentFontSizeSp = newValue.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            runtime.showFontSizeIndicator = true
        },
        onValueChangeFinished = {
            context.callbacks.ui.onRsvpFontSizeChange(runtime.currentFontSizeSp)
        },
        valueRange = MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP
    )
}

@Composable
private fun RsvpQuickSettingsHints() {
    Text(
        "Swipe up/down to adjust speed\nUse sliders to preview changes\nLong press to exit",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RsvpQuickSettingsAdvanced(
    context: RsvpUiContext,
    onBack: () -> Unit
) {
    val runtime = context.runtime
    val profile = context.state.profile

    SettingsNavRow(
        title = "Back",
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        showChevron = false,
        onClick = onBack
    )
    Text(
        "RSVP settings",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )

    val configForSettings = remember(profile.config, runtime.currentTempoMsPerWord) {
        profile.config.copy(tempoMsPerWord = runtime.currentTempoMsPerWord)
    }
    RsvpSettingsContent(
        selectedProfileId = profile.selectedProfileId,
        customProfiles = profile.customProfiles,
        config = configForSettings,
        unlockExtremeSpeed = context.state.uiPrefs.extremeSpeedUnlocked,
        rsvpFontSizeSp = runtime.currentFontSizeSp,
        rsvpTextBrightness = runtime.currentTextBrightness,
        rsvpFontFamily = runtime.currentFontFamily,
        rsvpFontWeight = runtime.currentFontWeight,
        rsvpVerticalBias = runtime.currentVerticalBias,
        rsvpHorizontalBias = runtime.currentHorizontalBias,
        onSelectProfile = context.callbacks.preferences.onSelectProfile,
        onSaveCustomProfile = context.callbacks.preferences.onSaveCustomProfile,
        onDeleteCustomProfile = context.callbacks.preferences.onDeleteCustomProfile,
        onConfigChange = { updated ->
            runtime.currentTempoMsPerWord = updated.tempoMsPerWord
            context.callbacks.preferences.onRsvpConfigChange(updated)
        },
        onUnlockExtremeSpeedChange = context.callbacks.preferences.onExtremeSpeedUnlockedChange,
        onRsvpFontSizeChange = { size ->
            runtime.currentFontSizeSp = size
            runtime.showFontSizeIndicator = true
            context.callbacks.ui.onRsvpFontSizeChange(size)
        },
        onRsvpTextBrightnessChange = { brightness ->
            runtime.currentTextBrightness = brightness
            context.callbacks.ui.onRsvpTextBrightnessChange(brightness)
        },
        onRsvpFontWeightChange = { weight ->
            runtime.currentFontWeight = weight
            context.callbacks.ui.onRsvpFontWeightChange(weight)
        },
        onRsvpFontFamilyChange = { family ->
            runtime.currentFontFamily = family
            context.callbacks.ui.onRsvpFontFamilyChange(family)
        },
        onRsvpVerticalBiasChange = { bias ->
            runtime.currentVerticalBias = bias
            context.callbacks.theme.onVerticalBiasChange(bias)
        },
        onRsvpHorizontalBiasChange = { bias ->
            runtime.currentHorizontalBias = bias
            context.callbacks.theme.onHorizontalBiasChange(bias)
        }
    )
}

@Composable
private fun BoxScope.RsvpBottomControls(context: RsvpUiContext) {
    val runtime = context.runtime

    AnimatedVisibility(
        visible = runtime.showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = QUICK_SETTINGS_BACKGROUND_ALPHA
                    )
                )
                .padding(CONTROLS_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RsvpPlaybackControlsRow(context)
            Spacer(modifier = Modifier.height(CONTROLS_SPACER))
            Text(
                "${runtime.frameIndex + 1} / ${context.frameState.frames.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = PROGRESS_TEXT_ALPHA)
            )
            Spacer(modifier = Modifier.height(CONTROLS_HINT_SPACER))
            Text(
                "Tap anywhere to resume",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = RESUME_TEXT_ALPHA)
            )
        }
    }
}

@Composable
private fun RsvpPlaybackControlsRow(context: RsvpUiContext) {
    val runtime = context.runtime

    Row(
        horizontalArrangement = Arrangement.spacedBy(CONTROLS_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            runtime.frameIndex = (runtime.frameIndex - 1).coerceAtLeast(0)
            runtime.completed = false
        }) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(SKIP_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = {
                runtime.isPlaying = !runtime.isPlaying
                if (runtime.isPlaying) runtime.showControls = false
            },
            modifier = Modifier
                .size(PLAY_BUTTON_SIZE)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                if (runtime.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (runtime.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(PLAY_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        IconButton(onClick = { advanceFrame(context) }) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(SKIP_ICON_SIZE),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun resolveCurrentTokenIndex(
    frames: List<com.example.kairo.core.model.RsvpFrame>,
    frameIndex: Int,
    fallbackIndex: Int
): Int {
    return frames.getOrNull(frameIndex)?.originalTokenIndex ?: fallbackIndex
}

private fun alignFrameIndex(
    frames: List<com.example.kairo.core.model.RsvpFrame>,
    tokenIndex: Int
): Int {
    if (frames.isEmpty()) return 0
    val idx = frames.indexOfLast { it.originalTokenIndex <= tokenIndex }
    val safeIdx = if (idx == -1) 0 else idx
    return safeIdx.coerceIn(0, frames.lastIndex)
}

private fun exitAndSavePosition(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val book = context.state.book

    runtime.isPlaying = false
    runtime.completed = true

    val currentIndex = resolveCurrentTokenIndex(frames, runtime.frameIndex, book.startIndex)
    val safeIndex = if (book.tokens.isNotEmpty()) {
        book.tokens.nearestWordIndex(currentIndex)
    } else {
        currentIndex
    }
    context.callbacks.playback.onPositionChanged(safeIndex)
    context.callbacks.playback.onExit(safeIndex)
}

private fun enterPositioningMode(runtime: RsvpRuntimeState) {
    runtime.wasPlayingBeforePositioning = runtime.isPlaying
    runtime.isPlaying = false
    runtime.showControls = false
    runtime.showQuickSettings = false
    runtime.showTempoIndicator = false
    runtime.isPositioningMode = true
    runtime.isAdjustingPosition = true
}

private fun finishPositioning(context: RsvpUiContext, resumeIfWasPlaying: Boolean) {
    val runtime = context.runtime
    if (!runtime.isPositioningMode) return

    runtime.isPositioningMode = false
    runtime.isAdjustingPosition = false
    context.callbacks.theme.onVerticalBiasChange(runtime.currentVerticalBias)
    if (resumeIfWasPlaying && runtime.wasPlayingBeforePositioning && !runtime.completed) {
        runtime.isPlaying = true
    }
}

private fun addBookmarkNow(context: RsvpUiContext) {
    val runtime = context.runtime
    val book = context.state.book
    if (book.tokens.isEmpty()) return

    val currentIndex = resolveCurrentTokenIndex(
        context.frameState.frames,
        runtime.frameIndex,
        book.startIndex
    )
    val safeIndex = book.tokens.nearestWordIndex(currentIndex)
        .coerceIn(0, book.tokens.lastIndex)
    val preview = book.tokens.getOrNull(safeIndex)?.text.orEmpty()
    context.callbacks.bookmarks.onAddBookmark(safeIndex, preview)
    runtime.showQuickSettings = false
}

private fun handleTap(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        finishPositioning(context, resumeIfWasPlaying = true)
    } else if (runtime.showQuickSettings) {
        runtime.showQuickSettings = false
    } else if (!runtime.completed) {
        runtime.isPlaying = !runtime.isPlaying
        runtime.showTempoIndicator = false
        runtime.showFontSizeIndicator = false
        runtime.showControls = !runtime.isPlaying
    }
}

private fun handleDragStart(runtime: RsvpRuntimeState) {
    runtime.dragAccumulator = ZERO_FLOAT
    runtime.dragStartTempoMsPerWord = runtime.currentTempoMsPerWord
    runtime.dragStartBias = runtime.currentVerticalBias
    runtime.dragStartHorizontalBias = runtime.currentHorizontalBias
}

private fun handleDrag(context: RsvpUiContext, dragAmount: androidx.compose.ui.geometry.Offset) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        val biasPerPx = POSITIONING_BIAS_PER_PX
        runtime.currentVerticalBias =
            (runtime.currentVerticalBias + dragAmount.y * biasPerPx).coerceIn(
                VERTICAL_BIAS_MIN,
                VERTICAL_BIAS_MAX
            )
        runtime.currentHorizontalBias =
            (runtime.currentHorizontalBias + dragAmount.x * biasPerPx).coerceIn(
                HORIZONTAL_BIAS_MIN,
                HORIZONTAL_BIAS_MAX
            )
        runtime.isAdjustingPosition = true
    } else {
        runtime.dragAccumulator += dragAmount.y
        val tempoDeltaMs =
            (runtime.dragAccumulator / TEMPO_SWIPE_THRESHOLD_PX).toInt() * TEMPO_STEP_MS
        if (tempoDeltaMs != 0L) {
            val newTempo =
                (runtime.dragStartTempoMsPerWord + tempoDeltaMs)
                    .coerceIn(context.timing.minTempoMs, context.timing.maxTempoMs)
            if (newTempo != runtime.currentTempoMsPerWord) {
                runtime.currentTempoMsPerWord = newTempo
                runtime.showTempoIndicator = true
            }
        }
    }
}

private fun handleDragEnd(context: RsvpUiContext) {
    val runtime = context.runtime
    if (runtime.isPositioningMode) {
        if (runtime.currentVerticalBias != runtime.dragStartBias) {
            context.callbacks.theme.onVerticalBiasChange(runtime.currentVerticalBias)
        }
        if (runtime.currentHorizontalBias != runtime.dragStartHorizontalBias) {
            context.callbacks.theme.onHorizontalBiasChange(runtime.currentHorizontalBias)
        }
    } else if (runtime.currentTempoMsPerWord != runtime.dragStartTempoMsPerWord) {
        context.callbacks.playback.onTempoChange(runtime.currentTempoMsPerWord)
        runtime.showTempoIndicator = false
    }
    runtime.dragAccumulator = ZERO_FLOAT
}

private fun advanceFrame(context: RsvpUiContext) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    if (frames.isEmpty()) return

    if (runtime.frameIndex < frames.lastIndex) {
        runtime.frameIndex += 1
        return
    }
    val lastFrame = frames.getOrNull(runtime.frameIndex)
    val rawNextIndex = (lastFrame?.originalTokenIndex ?: context.state.book.startIndex) + 1
    val safeNextIndex = if (context.state.book.tokens.isNotEmpty()) {
        context.state.book.tokens.nearestWordIndex(rawNextIndex)
    } else {
        rawNextIndex
    }
    context.callbacks.playback.onFinished(safeNextIndex)
    runtime.completed = true
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
/**
 * Composable that displays text with ORP (Optimal Recognition Point) alignment.
 * Uses a single Text with AnnotatedString for zero-jitter rendering.
 * The pivot character is always at the exact center of the screen.
 */
@Composable
private fun OrpAlignedText(
    tokens: List<Token>,
    typography: OrpTypography,
    colors: OrpColors,
    layout: OrpTextLayout
) {
    val content = remember(tokens) { buildOrpTextContent(tokens) }
    val rendering = rememberOrpRendering(
        fullText = content.fullText,
        typography = typography,
        pivotPosition = content.pivotPosition,
        pivotColor = colors.pivotColor
    )
    OrpAlignedTextLayout(
        content = content,
        layout = layout,
        colors = colors,
        rendering = rendering
    )
}

private data class OrpTextContent(
    val fullText: String,
    val firstWordStart: Int,
    val firstWordEndExclusive: Int,
    val pivotPosition: Int,
    val wordCount: Int
)

private data class OrpTextRendering(
    val annotatedText: AnnotatedString,
    val textStyle: TextStyle,
    val textMeasurer: TextMeasurer
)

private data class OrpBounds(
    val safeLeftPx: Float,
    val safeRightPx: Float,
    val desiredPivotX: Float,
    val maxTranslationX: Float,
    val maxWidthPx: Float
)

private data class OrpPivotRange(
    val start: Int,
    val end: Int,
    val safePivotIndex: Int
)

private data class OrpLayoutResult(
    val pivotIndex: Int,
    val translationX: Float,
    val guideBias: Float
)

private data class PivotAlignment(
    val pivotX: Float,
    val translationX: Float
)

@Composable
private fun rememberOrpRendering(
    fullText: String,
    typography: OrpTypography,
    pivotPosition: Int,
    pivotColor: Color
): OrpTextRendering {
    val baseStyle = MaterialTheme.typography.displayMedium
    val textStyle = remember(typography, baseStyle) {
        baseStyle.copy(
            fontFamily = typography.fontFamily,
            fontSize = typography.fontSizeSp.sp,
            fontWeight = typography.fontWeight,
            letterSpacing = ORP_LETTER_SPACING_SP.sp
        )
    }
    val annotatedText = remember(fullText, pivotPosition, pivotColor) {
        buildOrpAnnotatedText(fullText, pivotPosition, pivotColor)
    }
    val textMeasurer = rememberTextMeasurer()
    return OrpTextRendering(
        annotatedText = annotatedText,
        textStyle = textStyle,
        textMeasurer = textMeasurer
    )
}

private fun buildOrpAnnotatedText(
    fullText: String,
    pivotPosition: Int,
    pivotColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        append(fullText)
        if (fullText.isNotEmpty()) {
            val safeIndex = pivotPosition.coerceIn(0, fullText.lastIndex)
            addStyle(
                style = SpanStyle(color = pivotColor),
                start = safeIndex,
                end = (safeIndex + 1).coerceAtMost(fullText.length)
            )
        }
    }
}

private fun buildOrpTextContent(tokens: List<Token>): OrpTextContent {
    val firstWord = tokens.firstOrNull { it.type == TokenType.WORD }
    val wordCount = tokens.count { it.type == TokenType.WORD }
    var firstWordStart = INVALID_INDEX
    var firstWordEndExclusive = INVALID_INDEX

    val fullText = buildString {
        var needsSpace = false
        tokens.forEach { token ->
            when (token.type) {
                TokenType.WORD -> {
                    if (needsSpace) append(" ")
                    val start = length
                    append(token.text)
                    if (firstWordStart == INVALID_INDEX) {
                        firstWordStart = start
                        firstWordEndExclusive = length
                    }
                    needsSpace = true
                }
                TokenType.PUNCTUATION -> {
                    val ch = token.text.singleOrNull()
                    val isOpening = when (ch) {
                        null -> false
                        '"' -> !needsSpace
                        in ORP_OPENING_PUNCTUATION -> true
                        else -> false
                    }
                    if (isOpening && needsSpace) append(" ")
                    append(token.text)
                    needsSpace = !isOpening
                }
                TokenType.PARAGRAPH_BREAK, TokenType.PAGE_BREAK -> Unit
            }
        }
    }

    val wordStart = if (firstWordStart >= 0) firstWordStart else INVALID_INDEX
    val wordEndExclusive = if (firstWordEndExclusive > 0) firstWordEndExclusive else INVALID_INDEX
    val pivotPosition = if (firstWord != null && wordStart >= 0) {
        val wordEnd = (wordEndExclusive - 1).coerceAtLeast(wordStart)
        val wordLength = (wordEndExclusive - wordStart).coerceAtLeast(1)
        val centerOffset = ((wordLength - 1) / BIAS_SCALE_FACTOR).roundToInt()
        (wordStart + centerOffset).coerceIn(wordStart, wordEnd)
    } else {
        DEFAULT_PIVOT_INDEX
    }

    return OrpTextContent(
        fullText = fullText,
        firstWordStart = firstWordStart,
        firstWordEndExclusive = firstWordEndExclusive,
        pivotPosition = pivotPosition,
        wordCount = wordCount
    )
}

@Composable
private fun OrpAlignedTextLayout(
    content: OrpTextContent,
    layout: OrpTextLayout,
    colors: OrpColors,
    rendering: OrpTextRendering
) {
    val density = LocalDensity.current
    val effectiveBias = layout.horizontalBias.coerceIn(HORIZONTAL_BIAS_MIN, HORIZONTAL_BIAS_MAX)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ORP_HORIZONTAL_PADDING)
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(MIN_ORP_WIDTH_PX)
        val measured = remember(rendering.annotatedText, rendering.textStyle) {
            rendering.textMeasurer.measure(
                text = rendering.annotatedText,
                style = rendering.textStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = Int.MAX_VALUE)
            )
        }
        val measuredWidthPx = measured.size.width.toFloat()
        val baseEdgePx = with(density) { ORP_BASE_EDGE.toPx() }
        val extraEdgePx = with(density) { ORP_EXTRA_EDGE.toPx() }
        val bounds = calculateOrpBounds(
            maxWidthPx = maxWidthPx,
            effectiveBias = effectiveBias,
            baseEdgePx = baseEdgePx,
            extraEdgePx = extraEdgePx,
            measuredWidthPx = measuredWidthPx
        )
        val safePivotIndex = if (content.fullText.isEmpty()) {
            DEFAULT_PIVOT_INDEX
        } else {
            content.pivotPosition.coerceIn(0, content.fullText.lastIndex)
        }
        val pivotRange = calculatePivotRange(content, content.fullText.lastIndex, safePivotIndex)
        val layoutResult = calculateOrpLayout(content, layout, measured, bounds, pivotRange)

        Column(modifier = Modifier.fillMaxWidth()) {
            OrpStaticLine(colors.pivotLineColor)
            OrpPointer(layoutResult.guideBias, colors.pivotLineColor)
            Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
            OrpTextLine(rendering, colors.textColor, layoutResult.translationX)
            Spacer(modifier = Modifier.height(ORP_TEXT_SPACER))
            OrpPointer(layoutResult.guideBias, colors.pivotLineColor)
            OrpStaticLine(colors.pivotLineColor)
        }
    }
}

private fun calculateOrpBounds(
    maxWidthPx: Float,
    effectiveBias: Float,
    baseEdgePx: Float,
    extraEdgePx: Float,
    measuredWidthPx: Float
): OrpBounds {
    val rawFraction = ((effectiveBias + ONE_FLOAT) / BIAS_SCALE_FACTOR)
        .coerceIn(ORP_BIAS_FRACTION_MIN, ORP_BIAS_FRACTION_MAX)
    val biasFromCenter = rawFraction - ORP_CENTER_FRACTION
    val leftExtraFactor = (biasFromCenter / ORP_CENTER_FRACTION)
        .coerceAtLeast(ZERO_FLOAT)
        .coerceIn(ZERO_FLOAT, ONE_FLOAT)
    val rightExtraFactor = (-biasFromCenter / ORP_CENTER_FRACTION)
        .coerceAtLeast(ZERO_FLOAT)
        .coerceIn(ZERO_FLOAT, ONE_FLOAT)
    var safeLeftPx = baseEdgePx + (leftExtraFactor * extraEdgePx)
    var safeRightPx = baseEdgePx + (rightExtraFactor * extraEdgePx)
    val edgeTotal = safeLeftPx + safeRightPx
    if (edgeTotal > maxWidthPx && edgeTotal > ZERO_FLOAT) {
        val scale = maxWidthPx / edgeTotal
        safeLeftPx *= scale
        safeRightPx *= scale
    }
    val maxPivotXRaw = maxWidthPx - safeRightPx
    if (maxPivotXRaw < safeLeftPx) {
        val midpoint = maxWidthPx / BIAS_SCALE_FACTOR
        safeLeftPx = midpoint
        safeRightPx = maxWidthPx - midpoint
    }

    var minFraction = (safeLeftPx / maxWidthPx)
        .coerceIn(ORP_EDGE_FRACTION_MIN, ORP_EDGE_FRACTION_MAX)
    var maxFraction = ONE_FLOAT - (safeRightPx / maxWidthPx)
        .coerceIn(ORP_EDGE_FRACTION_MIN, ORP_EDGE_FRACTION_MAX)
    if (minFraction > maxFraction) {
        val midpoint = (minFraction + maxFraction) / BIAS_SCALE_FACTOR
        minFraction = midpoint
        maxFraction = midpoint
    }
    val desiredFraction = rawFraction.coerceIn(minFraction, maxFraction)
    val maxPivotX = maxWidthPx - safeRightPx
    val desiredPivotX = (maxWidthPx * desiredFraction)
        .coerceIn(safeLeftPx, maxPivotX)
    val maxTranslationX = maxWidthPx - safeRightPx - measuredWidthPx

    return OrpBounds(
        safeLeftPx = safeLeftPx,
        safeRightPx = safeRightPx,
        desiredPivotX = desiredPivotX,
        maxTranslationX = maxTranslationX,
        maxWidthPx = maxWidthPx
    )
}

private fun calculatePivotRange(
    content: OrpTextContent,
    lastIndex: Int,
    safePivotIndex: Int
): OrpPivotRange {
    val start = if (content.firstWordStart >= 0) content.firstWordStart else DEFAULT_PIVOT_INDEX
    val rawEnd = if (content.firstWordEndExclusive > 0) {
        content.firstWordEndExclusive - 1
    } else {
        lastIndex
    }
    val end = rawEnd.coerceAtLeast(start)
    return OrpPivotRange(start = start, end = end, safePivotIndex = safePivotIndex)
}

private fun calculateOrpLayout(
    content: OrpTextContent,
    layout: OrpTextLayout,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    pivotRange: OrpPivotRange
): OrpLayoutResult {
    return when {
        layout.lockPivot && content.wordCount > ORP_LOCK_PIVOT_WORDS ->
            layoutLockedPivot(pivotRange, measured, bounds, content.fullText)
        bounds.maxTranslationX < bounds.safeLeftPx ->
            layoutWideWord(pivotRange, measured, bounds, content.fullText.lastIndex)
        else ->
            layoutFlexiblePivot(pivotRange, measured, bounds, content.fullText)
    }
}

private fun layoutLockedPivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    fullText: String
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.coerceIn(pivotRange.start, pivotRange.end)
    val lastIndex = fullText.lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    val pivotCenter = pivotCenterX(measured, pivotIndex, lastIndex)
    val textLeft = measured.getBoundingBox(DEFAULT_PIVOT_INDEX).left
    val textRight = measured.getBoundingBox(lastIndex).right
    val textCenter = (textLeft + textRight) / BIAS_SCALE_FACTOR
    val chunkingShiftPx = pivotCenter - textCenter
    val minPivotX = bounds.safeLeftPx + pivotCenter
    val maxPivotX = bounds.maxTranslationX + pivotCenter
    val guidePivotX = (bounds.desiredPivotX + chunkingShiftPx).coerceIn(minPivotX, maxPivotX)
    val translationX = guidePivotX - pivotCenter
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX)
    )
}

private fun layoutWideWord(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    lastIndex: Int
): OrpLayoutResult {
    val pivotIndex = pivotRange.safePivotIndex.coerceIn(pivotRange.start, pivotRange.end)
    val measuredWidthPx = measured.size.width.toFloat()
    val translationX = (bounds.maxWidthPx - measuredWidthPx) / BIAS_SCALE_FACTOR
    val pivotCenter = pivotCenterX(
        measured,
        pivotIndex,
        lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    )
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = pivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX)
    )
}

private fun layoutFlexiblePivot(
    pivotRange: OrpPivotRange,
    measured: TextLayoutResult,
    bounds: OrpBounds,
    fullText: String
): OrpLayoutResult {
    val lastIndex = fullText.lastIndex.coerceAtLeast(DEFAULT_PIVOT_INDEX)
    val basePivotIndex = pivotRange.safePivotIndex
    val pivotCenter = pivotCenterX(measured, basePivotIndex, lastIndex)
    val translationX = (bounds.desiredPivotX - pivotCenter)
        .coerceIn(bounds.safeLeftPx, bounds.maxTranslationX)
    val alignment = alignPivotToPixel(pivotCenter, translationX)
    return OrpLayoutResult(
        pivotIndex = basePivotIndex,
        translationX = alignment.translationX,
        guideBias = guideBias(bounds.maxWidthPx, alignment.pivotX)
    )
}

private fun pivotCenterX(measured: TextLayoutResult, index: Int, lastIndex: Int): Float {
    val safeIndex = index.coerceIn(DEFAULT_PIVOT_INDEX, lastIndex)
    val box = measured.getBoundingBox(safeIndex)
    return box.left + (box.width / BIAS_SCALE_FACTOR)
}

private fun guideBias(maxWidthPx: Float, guidePivotX: Float): Float {
    return ((guidePivotX / maxWidthPx) * BIAS_SCALE_FACTOR) - ONE_FLOAT
}

private fun alignPivotToPixel(pivotCenter: Float, translationX: Float): PivotAlignment {
    val actualPivotX = pivotCenter + translationX
    val roundedPivotX = actualPivotX.roundToInt().toFloat()
    val adjustedTranslation = translationX + (roundedPivotX - actualPivotX)
    return PivotAlignment(pivotX = roundedPivotX, translationX = adjustedTranslation)
}

@Composable
private fun OrpStaticLine(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ORP_LINE_HEIGHT)
            .background(color)
    )
}

@Composable
private fun OrpPointer(guideBias: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ORP_POINTER_HEIGHT)
    ) {
        Box(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = guideBias, verticalBias = CENTER_BIAS))
                .width(ORP_POINTER_WIDTH)
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
private fun OrpTextLine(
    rendering: OrpTextRendering,
    textColor: Color,
    translationX: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Text(
            text = rendering.annotatedText,
            style = rendering.textStyle,
            color = textColor,
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(
                        ORP_TRANSFORM_ORIGIN_X,
                        ORP_TRANSFORM_ORIGIN_Y
                    )
                    this.translationX = translationX
                }
        )
    }
}

private const val DEFAULT_FONT_SIZE_SP = 44f
private val DEFAULT_FONT_FAMILY = RsvpFontFamily.INTER
private val DEFAULT_FONT_WEIGHT = RsvpFontWeight.LIGHT
private const val DEFAULT_TEXT_BRIGHTNESS = 0.88f
private const val DEFAULT_VERTICAL_BIAS = -0.15f
private const val DEFAULT_HORIZONTAL_BIAS = 0f

private const val SAFE_MIN_TEMPO_MS_PER_WORD = 30L
private const val EXTREME_MIN_TEMPO_MS_PER_WORD = 10L
private const val MAX_TEMPO_MS_PER_WORD = 240L
private const val DEFAULT_TEMPO_SCALE = 1.0
private const val TEMPO_SCALE_MIN = 0.1
private const val TEMPO_SCALE_MAX = 4.0
private const val MIN_FRAME_DELAY_MS = 16L
private const val POSITION_SAVE_INTERVAL_MS = 750L
private const val TEMPO_STEP_MS = 5L
private const val TEMPO_SWIPE_THRESHOLD_PX = 30f
private const val POSITIONING_BIAS_PER_PX = 0.0015f

private const val LOADING_TEXT_ALPHA = 0.6f
private const val PROGRESS_PRIMARY_ALPHA = 0.7f
private const val PROGRESS_TRACK_ALPHA = 0.3f
private const val TOP_BAR_ICON_ALPHA = 0.3f
private const val INDICATOR_BACKGROUND_ALPHA = 0.9f
private const val QUICK_SETTINGS_BACKGROUND_ALPHA = 0.95f
private const val POSITIONING_LINE_ALPHA = 0.18f
private const val PROGRESS_TEXT_ALPHA = 0.6f
private const val RESUME_TEXT_ALPHA = 0.4f

private const val TEXT_BRIGHTNESS_MIN = 0.55f
private const val TEXT_BRIGHTNESS_MAX = 1.0f
private const val PIVOT_LINE_ALPHA_BASE = 0.15f
private const val PIVOT_LINE_ALPHA_MIN = 0f
private const val PIVOT_LINE_ALPHA_MAX = 1f

private const val VERTICAL_BIAS_MIN = -0.7f
private const val VERTICAL_BIAS_MAX = 0.7f
private const val HORIZONTAL_BIAS_MIN = -0.6f
private const val HORIZONTAL_BIAS_MAX = 0.6f

private const val MIN_FONT_SIZE_SP = 28f
private const val MAX_FONT_SIZE_SP = 80f

private const val CONTROLS_HIDE_DELAY_MS = 3000L
private const val TEMPO_INDICATOR_HIDE_DELAY_MS = 1500L
private const val FONT_SIZE_INDICATOR_HIDE_DELAY_MS = 1500L

private const val MS_PER_MINUTE = 60_000.0
private const val MIN_WORD_COUNT = 1
private const val MIN_TOTAL_MS = 1L

private const val DEFAULT_PIVOT_INDEX = 0
private const val INVALID_INDEX = -1
private const val ORP_LOCK_PIVOT_WORDS = 1
private const val ORP_LETTER_SPACING_SP = 0.5f
private const val ORP_BIAS_FRACTION_MIN = 0.05f
private const val ORP_BIAS_FRACTION_MAX = 0.95f
private const val ORP_EDGE_FRACTION_MIN = 0.05f
private const val ORP_EDGE_FRACTION_MAX = 0.45f
private const val ORP_CENTER_FRACTION = 0.5f
private const val ORP_TRANSFORM_ORIGIN_X = 0f
private const val ORP_TRANSFORM_ORIGIN_Y = 0.5f
private const val MIN_ORP_WIDTH_PX = 1f

private const val ZERO_FLOAT = 0f
private const val ONE_FLOAT = 1f
private const val CENTER_BIAS = 0f
private const val BIAS_SCALE_FACTOR = 2f

private val PROGRESS_HEIGHT = 3.dp
private val TOP_BAR_PADDING = 16.dp
private val TOP_BAR_SPACING = 8.dp
private val TOP_BAR_ICON_SIZE = 24.dp
private val TEMPO_INDICATOR_TOP_PADDING = 60.dp
private val FONT_SIZE_INDICATOR_TOP_PADDING = 112.dp
private val INDICATOR_CORNER_RADIUS = 8.dp
private val INDICATOR_PADDING_HORIZONTAL = 16.dp
private val INDICATOR_PADDING_VERTICAL = 8.dp
private val POSITIONING_INDICATOR_PADDING_HORIZONTAL = 14.dp
private val POSITIONING_INDICATOR_PADDING_VERTICAL = 8.dp
private val QUICK_SETTINGS_CORNER = 16.dp
private val QUICK_SETTINGS_HORIZONTAL_PADDING = 16.dp
private val QUICK_SETTINGS_VERTICAL_PADDING = 14.dp
private val QUICK_SETTINGS_SPACING = 12.dp
private const val QUICK_SETTINGS_HEIGHT_FRACTION = 0.85f
private val POSITION_GUIDE_HEIGHT = 1.dp

private val CONTROLS_PADDING = 24.dp
private val CONTROLS_SPACER = 16.dp
private val CONTROLS_HINT_SPACER = 8.dp
private val CONTROLS_ROW_SPACING = 32.dp
private val SKIP_ICON_SIZE = 32.dp
private val PLAY_BUTTON_SIZE = 64.dp
private val PLAY_ICON_SIZE = 36.dp

private val ORP_HORIZONTAL_PADDING = 8.dp
private val ORP_BASE_EDGE = 24.dp
private val ORP_EXTRA_EDGE = 14.dp
private val ORP_LINE_HEIGHT = 2.dp
private val ORP_POINTER_HEIGHT = 12.dp
private val ORP_POINTER_WIDTH = 2.dp
private val ORP_TEXT_SPACER = 4.dp

private val ORP_OPENING_PUNCTUATION = setOf('(', '[', '{', '\u201C', '\u2018')
