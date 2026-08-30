@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.rsvp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.reader.R
import com.kairo.reader.core.model.RsvpConfig
import com.kairo.reader.core.model.RsvpFontFamily
import com.kairo.reader.core.model.RsvpFontWeight
import com.kairo.reader.core.model.prefersOrpWindowing
import com.kairo.reader.core.rsvp.RsvpSpeedControl
import com.kairo.reader.ui.WindowContainerMetrics
import com.kairo.reader.ui.bionic.BionicReadingText
import com.kairo.reader.ui.rememberWindowContainerMetrics
import com.kairo.reader.ui.theme.InterFontFamily
import com.kairo.reader.ui.theme.RobotoFontFamily
import com.kairo.reader.ui.tutorial.StartingTutorialOverlay
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialTargetIds
import com.kairo.reader.ui.tutorial.startingTutorialTarget
import kotlin.math.roundToInt

@Composable
internal fun RsvpPlaybackSurface(
    context: RsvpUiContext,
    tutorialState: StartingTutorialOverlayState? = null,
    onTutorialNext: () -> Unit = {},
    onTutorialPrevious: () -> Unit = {},
    onTutorialSkip: () -> Unit = {},
) {
    val runtime = context.runtime
    val frames = context.frameState.frames
    val rsvpDisplayFrame =
        resolveRsvpDisplayFrame(
            frames = frames,
            frameIndex = runtime.frameIndex,
            contextAssistMode = context.state.profile.config.contextAssistMode,
        )
    val windowMetrics = rememberWindowContainerMetrics()
    val compactLandscape = windowMetrics.isCompactLandscape(COMPACT_LANDSCAPE_MAX_HEIGHT_DP.dp)
    val bottomChromeInset =
        rememberBottomChromeInset(
            runtime = runtime,
            compactLandscape = compactLandscape,
            windowMetrics = windowMetrics,
        )
    val typography =
        OrpTypography(
            fontSizeSp = runtime.currentFontSizeSp,
            fontFamily = resolveFontFamily(runtime.currentFontFamily),
            fontWeight = resolveFontWeight(runtime.currentFontWeight),
        )
    val colors =
        rememberRsvpTextColors(
            textBrightness = runtime.currentTextBrightness,
            config = context.state.profile.config,
        )
    val interactionSource = remember { MutableInteractionSource() }
    val displayedSpeed =
        rememberRsvpDisplayedSpeed(
            currentTempoMsPerWord = runtime.currentTempoMsPerWord,
            minTempoMs = context.timing.minTempoMs,
            maxTempoMs = context.timing.maxTempoMs,
        )
    val speedBandLabel =
        stringResource(
            rsvpSpeedBandLabelRes(
                tempoMsPerWord = runtime.currentTempoMsPerWord,
                extremeUnlocked = context.state.uiPrefs.extremeSpeedUnlocked,
            ),
        )
    val speedIndicatorText =
        stringResource(
            R.string.rsvp_reading_speed_indicator,
            speedBandLabel,
            displayedSpeed,
        )
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    val tutorialTargetsEnabled = tutorialState != null

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .rsvpTutorialTarget(tutorialTargetsEnabled, StartingTutorialTargetIds.RSVP_SURFACE) {
                    targetId,
                    bounds,
                ->
                tutorialTargets[targetId] = bounds
            }
            .rsvpGestureModifier(context, interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        when (context.presentationMode) {
            ReadingPresentationMode.RSVP -> {
                RsvpPositioningGrid(context, bottomChromeInset)
                RsvpFocusWord(context, rsvpDisplayFrame, typography, colors, bottomChromeInset)
                RsvpContextAssist(context, rsvpDisplayFrame, bottomChromeInset)
                RsvpPositionGuide(context, bottomChromeInset)
            }
            ReadingPresentationMode.BIONIC -> {
                BionicReadingText(
                    frames = frames,
                    tokens = context.state.book.tokens,
                    frameIndex = runtime.frameIndex,
                    runtime = runtime,
                    preferences = context.bionicPreferences,
                )
            }
        }
        RsvpProgressBar(context)
        RsvpTopBar(
            context = context,
            settingsModifier =
            Modifier.rsvpTutorialTarget(
                tutorialTargetsEnabled,
                StartingTutorialTargetIds.RSVP_TOP_SETTINGS,
            ) {
                    targetId,
                    bounds,
                ->
                tutorialTargets[targetId] = bounds
            },
            closeModifier =
            Modifier.rsvpTutorialTarget(
                tutorialTargetsEnabled,
                StartingTutorialTargetIds.RSVP_EXIT,
            ) {
                    targetId,
                    bounds,
                ->
                tutorialTargets[targetId] = bounds
            },
        )
        RsvpTempoIndicator(context, speedIndicatorText)
        RsvpFontSizeIndicator(context)
        RsvpPositioningIndicator(context)
        RsvpScrubTargetIndicator(context)
        RsvpQuickSettingsPanel(
            context = context,
            speedPercent = displayedSpeed,
            panelModifier =
            Modifier.rsvpTutorialTarget(
                tutorialTargetsEnabled,
                StartingTutorialTargetIds.RSVP_QUICK_SETTINGS,
            ) {
                    targetId,
                    bounds,
                ->
                tutorialTargets[targetId] = bounds
            },
            settingsRowModifier =
            Modifier.rsvpTutorialTarget(
                tutorialTargetsEnabled,
                StartingTutorialTargetIds.RSVP_SETTINGS_ROW,
            ) {
                    targetId,
                    bounds,
                ->
                tutorialTargets[targetId] = bounds
            },
        )
        RsvpBottomControls(
            context = context,
            speedIndicatorText = speedIndicatorText,
            controlsModifier =
            Modifier.rsvpTutorialTarget(
                tutorialTargetsEnabled,
                StartingTutorialTargetIds.RSVP_PLAYBACK_CONTROLS,
            ) {
                    targetId,
                    bounds,
                ->
                tutorialTargets[targetId] = bounds
            },
        )
        tutorialState?.let { overlayState ->
            StartingTutorialOverlay(
                state = overlayState,
                targetBounds = overlayState.step.targetId?.let(tutorialTargets::get),
                onNext = onTutorialNext,
                onPrevious = onTutorialPrevious,
                onSkip = onTutorialSkip,
            )
        }
    }
}

private fun Modifier.rsvpTutorialTarget(
    enabled: Boolean,
    targetId: String,
    onBoundsChanged: (String, Rect) -> Unit,
): Modifier =
    if (enabled) {
        startingTutorialTarget(targetId, onBoundsChanged)
    } else {
        this
    }

@Composable
internal fun rememberRsvpDisplayedSpeed(
    currentTempoMsPerWord: Long,
    minTempoMs: Long,
    maxTempoMs: Long,
): Int =
    remember(currentTempoMsPerWord, minTempoMs, maxTempoMs) {
        RsvpSpeedControl.displaySpeed(
            RsvpSpeedControl.speedForTempoMs(
                tempoMsPerWord = currentTempoMsPerWord,
                minTempoMsPerWord = minTempoMs,
                maxTempoMsPerWord = maxTempoMs,
            ),
        )
    }

@Composable
private fun rememberRsvpTextColors(
    textBrightness: Float,
    config: RsvpConfig,
): OrpColors {
    val clampedBrightness = textBrightness.coerceIn(TEXT_BRIGHTNESS_MIN, TEXT_BRIGHTNESS_MAX)
    val guideBrightness =
        config.orpGuideBrightness
            .toFloat()
            .coerceIn(ORP_GUIDE_BRIGHTNESS_MIN, ORP_GUIDE_BRIGHTNESS_MAX)
    val pivotLineAlpha =
        (PIVOT_LINE_ALPHA_BASE * clampedBrightness * guideBrightness)
            .coerceIn(PIVOT_LINE_ALPHA_MIN, PIVOT_LINE_ALPHA_MAX)
    return OrpColors(
        pivotColor = MaterialTheme.colorScheme.primary,
        pivotLineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = pivotLineAlpha),
        textColor = MaterialTheme.colorScheme.onBackground.copy(alpha = clampedBrightness),
        highlightColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RsvpFocusWord(
    context: RsvpUiContext,
    frame: com.kairo.reader.core.model.RsvpFrame?,
    typography: OrpTypography,
    colors: OrpColors,
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val profile = context.state.profile
    if (frame == null) return

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(bottom = bottomChromeInset),
        contentAlignment =
        BiasAlignment(
            horizontalBias = CENTER_BIAS,
            verticalBias =
            runtime.currentVerticalBias.coerceIn(
                VERTICAL_BIAS_MIN,
                VERTICAL_BIAS_MAX,
            ),
        ),
    ) {
        OrpAlignedText(
            tokens = frame.tokens,
            typography = typography,
            colors = colors,
            layout =
            OrpTextLayout(
                horizontalBias = runtime.currentHorizontalBias,
                lockPivot =
                profile.config.enablePhraseChunking &&
                    profile.config.maxWordsPerUnit.coerceAtLeast(2) > ORP_LOCK_PIVOT_WORDS,
                smoothTranslation = runtime.isScrubbing || runtime.isAdjustingPosition,
                preferWindowing = profile.config.prefersOrpWindowing(runtime.currentTempoMsPerWord),
                simplifyPunctuation = false,
                guideVisible = profile.config.orpGuideEnabled,
                pivotHighlightVisible = profile.config.orpHighlightEnabled,
                guideThickness =
                profile.config.orpGuideThickness
                    .toFloat()
                    .coerceIn(ORP_GUIDE_THICKNESS_MIN, ORP_GUIDE_THICKNESS_MAX),
            ),
        )
    }
}

/**
 * Adjustment grid shown in positioning mode: faint alignment lines at every grid bias on both
 * axes (center lines emphasized), matching the snap targets in the drag handler. Drawn in the
 * same padded space the word and ORP pivot are positioned in, so lines land exactly where the
 * word snaps.
 */
@Composable
private fun RsvpPositioningGrid(
    context: RsvpUiContext,
    bottomChromeInset: Dp,
) {
    val visible =
        context.runtime.isPositioningMode && context.state.uiPrefs.positioningGridEnabled
    val lineColor =
        MaterialTheme.colorScheme.onBackground.copy(alpha = POSITIONING_GRID_LINE_ALPHA)
    val centerColor =
        MaterialTheme.colorScheme.primary.copy(alpha = POSITIONING_GRID_CENTER_LINE_ALPHA)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Canvas(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomChromeInset)
                .padding(horizontal = ORP_HORIZONTAL_PADDING),
        ) {
            val strokeWidth = POSITION_GUIDE_HEIGHT.toPx()
            val verticalLineCount =
                (VERTICAL_BIAS_MAX / POSITIONING_GRID_SPACING_BIAS).roundToInt()
            for (index in -verticalLineCount..verticalLineCount) {
                val bias = index * POSITIONING_GRID_SPACING_BIAS
                val y = size.height * ((ONE_FLOAT + bias) / BIAS_SCALE_FACTOR)
                drawLine(
                    color = if (index == 0) centerColor else lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth,
                )
            }
            val horizontalLineCount =
                (HORIZONTAL_BIAS_MAX / POSITIONING_GRID_SPACING_BIAS).roundToInt()
            for (index in -horizontalLineCount..horizontalLineCount) {
                val bias = index * POSITIONING_GRID_SPACING_BIAS
                val x = size.width * ((ONE_FLOAT + bias) / BIAS_SCALE_FACTOR)
                drawLine(
                    color = if (index == 0) centerColor else lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
}

@Composable
private fun RsvpPositionGuide(
    context: RsvpUiContext,
    bottomChromeInset: Dp,
) {
    val runtime = context.runtime
    val visible =
        runtime.showQuickSettings || runtime.isAdjustingPosition || runtime.isPositioningMode

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomChromeInset),
            contentAlignment =
            BiasAlignment(
                horizontalBias = CENTER_BIAS,
                verticalBias =
                runtime.currentVerticalBias.coerceIn(
                    VERTICAL_BIAS_MIN,
                    VERTICAL_BIAS_MAX,
                ),
            ),
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(POSITION_GUIDE_HEIGHT)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = POSITIONING_LINE_ALPHA),
                    ),
            )
        }
    }
}

@Composable
private fun rememberBottomChromeInset(
    runtime: RsvpRuntimeState,
    compactLandscape: Boolean,
    windowMetrics: WindowContainerMetrics,
): Dp {
    if (!runtime.showControls) return 0.dp

    val controlsInset = rememberControlsChromeInset(compactLandscape)
    val density = LocalDensity.current
    val viewportHeightPx = windowMetrics.heightPx.toFloat().coerceAtLeast(1f)
    val controlsHeightPx = with(density) { controlsInset.toPx() }
    val orpDecorHeightPx =
        with(density) {
            (
                (ORP_LINE_HEIGHT * 2) +
                    (ORP_POINTER_HEIGHT * 2) +
                    (ORP_TEXT_SPACER * 2)
                ).toPx()
        }
    val orpTextHeightPx =
        with(density) {
            runtime.currentFontSizeSp
                .coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
                .sp
                .toPx() * ORP_COLLISION_TEXT_HEIGHT_MULTIPLIER
        }
    val orpBandHalfHeightPx = (orpDecorHeightPx + orpTextHeightPx) / BIAS_SCALE_FACTOR
    val orpCenterY =
        viewportHeightPx *
            (ONE_FLOAT + runtime.currentVerticalBias.coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX)) /
            BIAS_SCALE_FACTOR
    val clearancePx = with(density) { CONTROLS_ORP_CLEARANCE.toPx() }
    val reservedInsetPx =
        resolveControlsChromeInsetPx(
            viewportHeightPx = viewportHeightPx,
            controlsHeightPx = controlsHeightPx,
            orpCenterY = orpCenterY,
            orpBandHalfHeightPx = orpBandHalfHeightPx,
            verticalBias = runtime.currentVerticalBias,
            clearancePx = clearancePx,
        )

    return with(density) { reservedInsetPx.toDp() }
}

internal fun shouldReserveControlsChromeInset(
    viewportHeightPx: Float,
    controlsHeightPx: Float,
    orpCenterY: Float,
    orpBandHalfHeightPx: Float,
    clearancePx: Float,
): Boolean =
    resolveControlsChromeInsetPx(
        viewportHeightPx = viewportHeightPx,
        controlsHeightPx = controlsHeightPx,
        orpCenterY = orpCenterY,
        orpBandHalfHeightPx = orpBandHalfHeightPx,
        verticalBias = DEFAULT_VERTICAL_BIAS,
        clearancePx = clearancePx,
    ) > 0f

internal fun resolveControlsChromeInsetPx(
    viewportHeightPx: Float,
    controlsHeightPx: Float,
    orpCenterY: Float,
    orpBandHalfHeightPx: Float,
    verticalBias: Float,
    clearancePx: Float,
): Float {
    if (controlsHeightPx <= 0f) return 0f
    val controlsTopPx = viewportHeightPx - controlsHeightPx
    val overflowPx = orpCenterY + orpBandHalfHeightPx + clearancePx - controlsTopPx
    if (overflowPx <= 0f) return 0f

    val biasFactor =
        (ONE_FLOAT + verticalBias.coerceIn(VERTICAL_BIAS_MIN, VERTICAL_BIAS_MAX)) /
            BIAS_SCALE_FACTOR
    val insetPx = overflowPx / biasFactor.coerceAtLeast(MINIMUM_BIAS_FACTOR)
    return insetPx.coerceIn(0f, controlsHeightPx)
}

private const val MINIMUM_BIAS_FACTOR = 0.01f
private const val COMPACT_LANDSCAPE_MAX_HEIGHT_DP = 480

@Composable
private fun rememberControlsChromeInset(compactLandscape: Boolean): Dp {
    val density = LocalDensity.current
    val navigationBarsInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val controlsHeight =
        if (compactLandscape) {
            CONTROLS_COMPACT_RESERVED_HEIGHT
        } else {
            CONTROLS_RESERVED_HEIGHT
        }
    return controlsHeight + navigationBarsInset
}

internal fun resolveFontFamily(fontFamily: RsvpFontFamily): FontFamily =
    when (fontFamily) {
        RsvpFontFamily.INTER -> InterFontFamily
        RsvpFontFamily.ROBOTO -> RobotoFontFamily
    }

internal fun resolveFontWeight(fontWeight: RsvpFontWeight): FontWeight =
    when (fontWeight) {
        RsvpFontWeight.LIGHT -> FontWeight.Light
        RsvpFontWeight.NORMAL -> FontWeight.Normal
        RsvpFontWeight.MEDIUM -> FontWeight.Medium
    }
