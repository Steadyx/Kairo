package com.kairo.reader.ui.reader

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderInlineImageSizingTest {
    @Test
    fun authoredDimensionsOverrideDifferingIntrinsicAspectRatio() {
        assertEquals(
            ReaderImageSize(widthPx = 800f, heightPx = 400f),
            mergeReaderImageSize(
                authoredSize = ReaderImageSize(widthPx = 800f, heightPx = 400f),
                intrinsicSize = ReaderImageSize(widthPx = 300f, heightPx = 600f),
            ),
        )
    }

    @Test
    fun authoredWidthUsesIntrinsicAspectRatioToInferHeight() {
        assertEquals(
            ReaderImageSize(widthPx = 240f, heightPx = 160f),
            mergeReaderImageSize(
                authoredSize = ReaderImageSize(widthPx = 240f),
                intrinsicSize = ReaderImageSize(widthPx = 1200f, heightPx = 800f),
            ),
        )
    }

    @Test
    fun authoredHeightUsesIntrinsicAspectRatioToInferWidth() {
        assertEquals(
            ReaderImageSize(widthPx = 225f, heightPx = 150f),
            mergeReaderImageSize(
                authoredSize = ReaderImageSize(heightPx = 150f),
                intrinsicSize = ReaderImageSize(widthPx = 1200f, heightPx = 800f),
            ),
        )
    }

    @Test
    fun intrinsicBoundsAreUsedWhenAuthoredDimensionsAreAbsent() {
        assertEquals(
            ReaderImageSize(widthPx = 640f, heightPx = 480f),
            mergeReaderImageSize(
                authoredSize = null,
                intrinsicSize = ReaderImageSize(widthPx = 640f, heightPx = 480f),
            ),
        )
    }

    @Test
    fun invalidDimensionsAreAbsentAndUnresolvedAuthoredSizeStaysIncomplete() {
        assertEquals(
            ReaderImageSize(widthPx = 640f, heightPx = 480f),
            mergeReaderImageSize(
                authoredSize = ReaderImageSize(widthPx = Float.NaN, heightPx = -1f),
                intrinsicSize = ReaderImageSize(widthPx = 640f, heightPx = 480f),
            ),
        )
        assertEquals(
            ReaderImageSize(widthPx = 240f),
            mergeReaderImageSize(
                authoredSize = ReaderImageSize(widthPx = 240f),
                intrinsicSize = null,
            ),
        )
        assertNull(
            mergeReaderImageSize(
                authoredSize = ReaderImageSize(widthPx = Float.POSITIVE_INFINITY),
                intrinsicSize = ReaderImageSize(widthPx = 0f, heightPx = Float.NaN),
            ),
        )
    }

    @Test
    fun completeDisplayGeometryIsCappedToAvailableWidth() {
        val result =
            requireNotNull(
                resolveInlineImageDisplaySize(
                    imageSize = ReaderImageSize(widthPx = 800f, heightPx = 400f),
                    availableWidth = 500.dp,
                ),
            )

        assertEquals(500.dp, result.width)
        assertEquals(2f, result.aspectRatio, 0.0001f)
    }

    @Test
    fun unresolvedDisplayGeometryUsesTheSameFixedFallback() {
        val missing =
            requireNotNull(
                resolveInlineImageDisplaySize(
                    imageSize = null,
                    availableWidth = 500.dp,
                ),
            )
        val incomplete =
            requireNotNull(
                resolveInlineImageDisplaySize(
                    imageSize = ReaderImageSize(widthPx = 240f),
                    availableWidth = 500.dp,
                ),
            )

        assertEquals(missing, incomplete)
        assertEquals(500.dp, missing.width)
        assertEquals(INLINE_IMAGE_FALLBACK_ASPECT_RATIO, missing.aspectRatio, 0f)
    }

    @Test
    fun quarterTurnExifOrientationsSwapDecodedBounds() {
        (5..8).forEach { orientation ->
            assertEquals(
                "orientation $orientation",
                ReaderImageSize(widthPx = 800f, heightPx = 1200f),
                orientReaderImageBounds(
                    width = 1200,
                    height = 800,
                    orientation = readerImageOrientationFromExifValue(orientation),
                ),
            )
        }
    }

    @Test
    fun nonQuarterTurnExifOrientationsKeepDecodedBounds() {
        (1..4).forEach { orientation ->
            assertEquals(
                "orientation $orientation",
                ReaderImageSize(widthPx = 1200f, heightPx = 800f),
                orientReaderImageBounds(
                    width = 1200,
                    height = 800,
                    orientation = readerImageOrientationFromExifValue(orientation),
                ),
            )
        }
    }
}
