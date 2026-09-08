package com.kairo.reader.data.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal object CoverImageOptimizer {
    fun optimize(coverImage: ByteArray?): ByteArray? {
        if (coverImage == null || coverImage.isEmpty()) return coverImage

        val safeFallback =
            coverImage.takeIf { it.size <= MAX_COVER_DB_BYTES }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, bounds)

            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching safeFallback

            // CursorWindow on many devices is ~2MB; keep cover comfortably under that, and also
            // cap pixel dimensions so first-time decode/render is fast.
            if (!needsOptimization(coverImage.size, width, height)) return@runCatching coverImage

            val sampleSize = calculateInSampleSize(width, height, COVER_MAX_DIM_PX)
            val decode =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val bitmap =
                BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size, decode)
                    ?: return@runCatching safeFallback

            try {
                val out = ByteArrayOutputStream()
                var quality = INITIAL_COVER_JPEG_QUALITY
                var encoded: ByteArray
                do {
                    out.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    encoded = out.toByteArray()
                    quality -= JPEG_QUALITY_STEP
                } while (encoded.size > MAX_COVER_DB_BYTES && quality >= MIN_COVER_JPEG_QUALITY)
                encoded
            } finally {
                bitmap.recycle()
            }
        }.getOrNull() ?: safeFallback
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimPx: Int,
    ): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDimPx || h > maxDimPx) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    internal fun needsOptimization(byteCount: Int, width: Int, height: Int): Boolean =
        byteCount > MAX_COVER_DB_BYTES || width > COVER_MAX_DIM_PX || height > COVER_MAX_DIM_PX

    private const val MAX_COVER_DB_BYTES = 256 * 1024
    private const val COVER_MAX_DIM_PX = 1080
    private const val INITIAL_COVER_JPEG_QUALITY = 90
    private const val JPEG_QUALITY_STEP = 10
    private const val MIN_COVER_JPEG_QUALITY = 60
}
