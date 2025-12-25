package com.example.kairo.ui.reader

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import java.io.File

@Composable
internal fun ChapterImages(
    imagePaths: List<String>,
    onImageClick: (String) -> Unit,
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
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable { onImageClick(relativePath) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InlineImageBlock(
    imagePath: String,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val file = remember(imagePath) { resolveImageFile(context, imagePath) }
    if (!file.exists()) return

    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        tonalElevation = 1.dp,
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(imagePath) },
    ) {
        SubcomposeAsyncImage(
            model = file,
            contentDescription = "Illustration",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val size = painter.intrinsicSize
            val contentModifier =
                if (size.width.isFinite() && size.height.isFinite() && size.height > 0f) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(size.width / size.height)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                }
            SubcomposeAsyncImageContent(modifier = contentModifier)
        }
    }
}

@Composable
internal fun FullScreenImageViewer(
    imagePath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(imagePath) { resolveImageFile(context, imagePath) }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(imagePath) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        if (file.exists()) {
            SubcomposeAsyncImage(
                model = file,
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        } else {
            Text(
                text = "Image not found",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close image viewer",
                tint = Color.White,
            )
        }
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
