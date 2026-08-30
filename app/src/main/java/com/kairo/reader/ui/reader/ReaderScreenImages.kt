package com.kairo.reader.ui.reader

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kairo.reader.R
import java.io.File

@Composable
internal fun ChapterImages(
    imagePaths: List<String>,
    onImageOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = imagePaths, key = { it }) { relativePath ->
            val file = remember(relativePath) { File(context.filesDir, relativePath) }
            if (file.exists()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp,
                    modifier =
                    Modifier
                        .size(width = 220.dp, height = 160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .openReaderImageOnLongPress(
                                    imagePath = relativePath,
                                    onOpen = onImageOpen,
                                ),
                            contentScale = ContentScale.Crop,
                        )
                        ReaderImageOpenHint(
                            modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineImageBlock(
    imagePath: String,
    imageSize: ReaderImageSize?,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val file = remember(imagePath) { resolveImageFile(context, imagePath) }
    if (!file.exists()) return

    val request =
        remember(file) {
            ImageRequest.Builder(context)
                .data(file)
                .crossfade(false)
                .build()
        }
    val illustrationDescription = stringResource(R.string.content_desc_illustration)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val displaySize =
            resolveInlineImageDisplaySize(
                imageSize = imageSize,
                availableWidth = availableWidth,
            )

        Box(
            modifier =
            Modifier
                .inlineImageDisplaySize(displaySize)
                .align(Alignment.Center),
        ) {
            AsyncImage(
                model = request,
                contentDescription = illustrationDescription,
                contentScale = ContentScale.Fit,
                modifier =
                Modifier
                    .fillMaxSize()
                    .openReaderImageOnLongPress(
                        imagePath = imagePath,
                        onOpen = onOpen,
                    ),
            )
            ReaderImageOpenHint(
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
            )
        }
    }
}

internal data class InlineImageDisplaySize(val width: Dp, val aspectRatio: Float,)

internal fun resolveInlineImageDisplaySize(
    imageSize: ReaderImageSize?,
    availableWidth: Dp,
): InlineImageDisplaySize? {
    val maximumWidth = availableWidth.value.validImageLengthOrNull() ?: return null
    val imageWidth = imageSize?.widthPx.validImageLengthOrNull()
    val imageHeight = imageSize?.heightPx.validImageLengthOrNull()
    val imageAspectRatio =
        if (imageWidth != null && imageHeight != null) {
            (imageWidth / imageHeight).validImageLengthOrNull()
        } else {
            null
        }

    return InlineImageDisplaySize(
        width =
        if (imageAspectRatio != null && imageWidth != null) {
            imageWidth.coerceAtMost(maximumWidth).dp
        } else {
            availableWidth
        },
        aspectRatio = imageAspectRatio ?: INLINE_IMAGE_FALLBACK_ASPECT_RATIO,
    )
}

private fun Modifier.inlineImageDisplaySize(displaySize: InlineImageDisplaySize?): Modifier =
    if (displaySize != null) {
        width(displaySize.width).aspectRatio(displaySize.aspectRatio)
    } else {
        fillMaxWidth().aspectRatio(INLINE_IMAGE_FALLBACK_ASPECT_RATIO)
    }

internal const val INLINE_IMAGE_FALLBACK_ASPECT_RATIO = 4f / 3f

@Composable
internal fun FullScreenImageViewer(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(imagePath) { resolveImageFile(context, imagePath) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var scale by remember(imagePath) { mutableFloatStateOf(1f) }
    var offset by remember(imagePath) { mutableStateOf(Offset.Zero) }
    var imageViewportSize by remember(imagePath) { mutableStateOf(IntSize.Zero) }

    fun clampedOffset(
        value: Offset,
        scaleValue: Float,
    ): Offset {
        if (scaleValue <= MIN_IMAGE_VIEWER_SCALE || imageViewportSize == IntSize.Zero) {
            return Offset.Zero
        }
        val minX = -(imageViewportSize.width * (scaleValue - 1f)).coerceAtLeast(0f)
        val minY = -(imageViewportSize.height * (scaleValue - 1f)).coerceAtLeast(0f)
        return Offset(
            x = value.x.coerceIn(minX, 0f),
            y = value.y.coerceIn(minY, 0f),
        )
    }

    fun offsetZoomedToward(
        focusPoint: Offset,
        nextScale: Float,
    ): Offset {
        val scaleFactor = nextScale / scale
        return clampedOffset(
            value = offset * scaleFactor + focusPoint * (1f - scaleFactor),
            scaleValue = nextScale,
        )
    }

    fun updateZoomFromGesture(
        focusPoint: Offset,
        panChange: Offset,
        zoomChange: Float,
    ) {
        val nextScale =
            (scale * zoomChange)
                .coerceIn(MIN_IMAGE_VIEWER_SCALE, MAX_IMAGE_VIEWER_SCALE)
        offset =
            if (nextScale <= MIN_IMAGE_VIEWER_SCALE) {
                Offset.Zero
            } else {
                clampedOffset(offsetZoomedToward(focusPoint, nextScale) + panChange, nextScale)
            }
        scale = nextScale
    }

    fun toggleDoubleTapZoom(tapOffset: Offset) {
        val nextScale =
            if (scale > MIN_IMAGE_VIEWER_SCALE) {
                MIN_IMAGE_VIEWER_SCALE
            } else {
                DOUBLE_TAP_IMAGE_VIEWER_SCALE
            }
        offset =
            if (nextScale <= MIN_IMAGE_VIEWER_SCALE) {
                Offset.Zero
            } else {
                offsetZoomedToward(tapOffset, nextScale)
            }
        scale = nextScale
    }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clipToBounds(),
    ) {
        if (file.exists()) {
            SubcomposeAsyncImage(
                model = file,
                contentDescription = stringResource(R.string.content_desc_full_screen_image),
                contentScale = ContentScale.Fit,
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .onSizeChanged { imageViewportSize = it }
                    .pointerInput(imagePath) {
                        detectTapGestures(
                            onTap = { currentOnDismiss() },
                            onDoubleTap = { tapOffset ->
                                toggleDoubleTapZoom(tapOffset)
                            },
                        )
                    }
                    .pointerInput(imagePath) {
                        detectTransformGestures { centroid, panChange, zoomChange, _ ->
                            updateZoomFromGesture(
                                focusPoint = centroid,
                                panChange = panChange,
                                zoomChange = zoomChange,
                            )
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        } else {
            Text(
                text = stringResource(R.string.reader_image_not_found),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_image_viewer_hint),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.content_desc_close_image_viewer),
                tint = Color.White,
            )
        }
    }
}

@Composable
internal fun ReaderImageOpenHint(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.reader_image_hold_to_view),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun Modifier.openReaderImageOnLongPress(
    imagePath: String,
    onOpen: (String) -> Unit,
): Modifier {
    val currentOnOpen by rememberUpdatedState(onOpen)
    val hapticFeedback = LocalHapticFeedback.current
    return pointerInput(imagePath) {
        detectTapGestures(
            onLongPress = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnOpen(imagePath)
            },
        )
    }
}

internal fun resolveImageFile(
    context: Context,
    imagePath: String,
): File =
    if (imagePath.startsWith("/")) {
        File(imagePath)
    } else {
        File(context.filesDir, imagePath)
    }

private const val MIN_IMAGE_VIEWER_SCALE = 1f
private const val MAX_IMAGE_VIEWER_SCALE = 5f
private const val DOUBLE_TAP_IMAGE_VIEWER_SCALE = 2.4f
