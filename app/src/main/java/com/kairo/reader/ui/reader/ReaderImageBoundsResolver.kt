package com.kairo.reader.ui.reader

import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

fun interface ReaderImageBoundsResolver {
    suspend fun resolve(imagePath: String): ReaderImageSize?

    companion object {
        val NoOp: ReaderImageBoundsResolver = ReaderImageBoundsResolver { null }
    }
}

internal class FileReaderImageBoundsResolver(private val filesDirectory: File, private val ioDispatcher: CoroutineDispatcher,) :
    ReaderImageBoundsResolver {
    override suspend fun resolve(imagePath: String): ReaderImageSize? =
        withContext(ioDispatcher) {
            try {
                val imageFile = resolveReaderAssetFile(filesDirectory, imagePath) ?: return@withContext null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.path, options)

                val width = options.outWidth.takeIf { it > 0 } ?: return@withContext null
                val height = options.outHeight.takeIf { it > 0 } ?: return@withContext null
                val orientation = readReaderImageOrientation(imageFile, options.outMimeType)

                orientReaderImageBounds(width, height, orientation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }
}

private fun resolveReaderAssetFile(
    filesDirectory: File,
    imagePath: String,
): File? {
    if (imagePath.isBlank()) return null

    val supplied = File(imagePath)
    val candidate =
        if (supplied.isAbsolute) {
            supplied.canonicalFile
        } else {
            File(filesDirectory, imagePath).canonicalFile
        }
    return candidate.takeIf { it.isFile }
}

internal fun mergeReaderImageSize(
    authoredSize: ReaderImageSize?,
    intrinsicSize: ReaderImageSize?,
): ReaderImageSize? {
    val authoredWidth = authoredSize?.widthPx.validImageLengthOrNull()
    val authoredHeight = authoredSize?.heightPx.validImageLengthOrNull()
    if (authoredWidth != null && authoredHeight != null) {
        return ReaderImageSize(widthPx = authoredWidth, heightPx = authoredHeight)
    }

    val intrinsicWidth = intrinsicSize?.widthPx.validImageLengthOrNull()
    val intrinsicHeight = intrinsicSize?.heightPx.validImageLengthOrNull()
    val intrinsicAspectRatio =
        if (intrinsicWidth != null && intrinsicHeight != null) {
            (intrinsicWidth / intrinsicHeight).validImageLengthOrNull()
        } else {
            null
        }

    if (authoredWidth != null && intrinsicAspectRatio != null) {
        val inferredHeight = (authoredWidth / intrinsicAspectRatio).validImageLengthOrNull()
        if (inferredHeight != null) {
            return ReaderImageSize(widthPx = authoredWidth, heightPx = inferredHeight)
        }
    }
    if (authoredHeight != null && intrinsicAspectRatio != null) {
        val inferredWidth = (authoredHeight * intrinsicAspectRatio).validImageLengthOrNull()
        if (inferredWidth != null) {
            return ReaderImageSize(widthPx = inferredWidth, heightPx = authoredHeight)
        }
    }
    if (intrinsicWidth != null && intrinsicHeight != null) {
        return ReaderImageSize(widthPx = intrinsicWidth, heightPx = intrinsicHeight)
    }

    return when {
        authoredWidth != null -> ReaderImageSize(widthPx = authoredWidth)
        authoredHeight != null -> ReaderImageSize(heightPx = authoredHeight)
        else -> null
    }
}

internal fun ReaderImageSize?.hasCompleteValidImageSize(): Boolean =
    this?.widthPx.validImageLengthOrNull() != null &&
        this?.heightPx.validImageLengthOrNull() != null

internal fun Float?.validImageLengthOrNull(): Float? =
    this?.takeIf { it.isFinite() && it > 0f }
