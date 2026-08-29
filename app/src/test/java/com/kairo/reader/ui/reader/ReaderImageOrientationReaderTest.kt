@file:Suppress("MagicNumber")

package com.kairo.reader.ui.reader

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageOrientationReaderTest {
    @Test
    fun jpegReadsLittleAndBigEndianSwapAndNonSwapOrientations() {
        listOf(
            Triple(FixtureByteOrder.LITTLE_ENDIAN, 6, ReaderImageOrientation.SWAP_DIMENSIONS),
            Triple(FixtureByteOrder.LITTLE_ENDIAN, 2, ReaderImageOrientation.NORMAL),
            Triple(FixtureByteOrder.BIG_ENDIAN, 8, ReaderImageOrientation.SWAP_DIMENSIONS),
            Triple(FixtureByteOrder.BIG_ENDIAN, 3, ReaderImageOrientation.NORMAL),
        ).forEach { (byteOrder, orientation, expected) ->
            assertEquals(
                "$byteOrder orientation $orientation",
                expected,
                readOrientation(
                    bytes = jpegWithExif(validTiff(byteOrder, orientation)),
                    mimeType = "image/jpeg",
                ),
            )
        }
    }

    @Test
    fun structurallyCompleteJpegWithoutExifIsNormal() {
        assertEquals(
            ReaderImageOrientation.NORMAL,
            readOrientation(jpegWithoutExif(), "image/jpeg"),
        )
    }

    @Test
    fun malformedTruncatedAndOverBudgetJpegsAreUnavailable() {
        val malformed = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x7F)
        val truncatedSegment =
            byteArrayOf(
                0xFF.toByte(),
                0xD8.toByte(),
                0xFF.toByte(),
                0xE1.toByte(),
                0x00,
                0x20,
                0x01,
            )

        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(malformed, "image/jpeg"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(truncatedSegment, "image/jpeg"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(jpegBeyondLogicalScanLimit(), "image/jpeg"),
        )
    }

    @Test
    fun jpegSegmentAndTiffEntryCountLimitsAreEnforced() {
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(jpegWithEmptySegments(count = 129), "image/jpeg"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(
                jpegWithExif(tiffWithDeclaredEntryCount(257)),
                "image/jpeg",
            ),
        )
    }

    @Test
    fun jpegMarkerFillRunIsBounded() {
        assertEquals(
            ReaderImageOrientation.NORMAL,
            readOrientation(jpegWithMarkerFill(fillCount = 32), "image/jpeg"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(jpegWithMarkerFill(fillCount = 33), "image/jpeg"),
        )
    }

    @Test
    fun forgedTiffOffsetIsUnavailableWithoutFollowingIt() {
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(
                jpegWithExif(tiffWithIfdOffset(UINT32_MAX)),
                "image/jpeg",
            ),
        )
    }

    @Test
    fun webpReadsExifAndHonorsOddChunkPadding() {
        val oddExifPayload =
            validTiff(FixtureByteOrder.LITTLE_ENDIAN, orientation = 7) + byteArrayOf(0x00)

        assertEquals(
            ReaderImageOrientation.SWAP_DIMENSIONS,
            readOrientation(
                webp(
                    minimalWebpImageChunks(hasExif = true) +
                        WebpChunk("EXIF", oddExifPayload),
                ),
                "image/webp",
            ),
        )
    }

    @Test
    fun webpSkipsLargePayloadAndReadsLateExif() {
        val encoded =
            webp(
                minimalWebpImageChunks(hasExif = true) +
                    listOf(
                        WebpChunk("JUNK", ByteArray(300 * 1024)),
                        WebpChunk(
                            "EXIF",
                            validTiff(FixtureByteOrder.LITTLE_ENDIAN, orientation = 6),
                        ),
                    ),
            )

        assertTrue(encoded.size > 256 * 1024)
        assertEquals(
            ReaderImageOrientation.SWAP_DIMENSIONS,
            readOrientation(encoded, "image/webp"),
        )
    }

    @Test
    fun webpAcceptsExifPrefixedTiffPayload() {
        val prefixedExif =
            EXIF_PREFIX + validTiff(FixtureByteOrder.BIG_ENDIAN, orientation = 8)

        assertEquals(
            ReaderImageOrientation.SWAP_DIMENSIONS,
            readOrientation(
                webp(
                    minimalWebpImageChunks(hasExif = true) +
                        WebpChunk("EXIF", prefixedExif),
                ),
                "image/webp",
            ),
        )
    }

    @Test
    fun structurallyCompleteWebpWithoutExifIsNormal() {
        assertEquals(
            ReaderImageOrientation.NORMAL,
            readOrientation(
                webp(minimalWebpImageChunks(hasExif = false)),
                "image/webp",
            ),
        )
    }

    @Test
    fun forgedWebpSizesAndChunkCountAreUnavailable() {
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(webpWithForgedRiffSize(), "image/webp"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(webpWithForgedChunkSize(), "image/webp"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(webpWithMissingOddChunkPadding(), "image/webp"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(
                webp(List(129) { WebpChunk("JUNK", byteArrayOf()) }),
                "image/webp",
            ),
        )
    }

    @Test
    fun oversizedWebpExifChunkIsRejectedBeforeTiffParsing() {
        val oversizedExif = ByteArray(WEBP_EXIF_LIMIT_BYTES + 1)
        validTiff(FixtureByteOrder.LITTLE_ENDIAN, orientation = 6)
            .copyInto(oversizedExif)

        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(
                webp(listOf(WebpChunk("EXIF", oversizedExif))),
                "image/webp",
            ),
        )
    }

    @Test
    fun mimeAndMagicMustBothIdentifySupportedOrRawBoundsFormats() {
        val jpeg = jpegWithExif(validTiff(FixtureByteOrder.LITTLE_ENDIAN, orientation = 6))
        val webp =
            webp(
                listOf(WebpChunk("EXIF", validTiff(FixtureByteOrder.LITTLE_ENDIAN, 6))),
            )

        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(jpeg, "image/webp"),
        )
        assertEquals(
            ReaderImageOrientation.UNAVAILABLE,
            readOrientation(webp, "image/jpeg"),
        )
        listOf("image/heif", "image/heic", "image/avif", null).forEach { unsupportedMime ->
            assertEquals(
                "mime $unsupportedMime",
                ReaderImageOrientation.UNAVAILABLE,
                readOrientation(jpeg, unsupportedMime),
            )
        }
        assertEquals(
            ReaderImageOrientation.NORMAL,
            readOrientation(PNG_SIGNATURE, "image/png"),
        )
    }
}

private fun readOrientation(
    bytes: ByteArray,
    mimeType: String?,
): ReaderImageOrientation {
    val imageFile = File.createTempFile("reader-image-orientation-", ".bin")
    return try {
        imageFile.writeBytes(bytes)
        readReaderImageOrientation(imageFile, mimeType)
    } finally {
        imageFile.delete()
    }
}

private fun jpegWithExif(tiff: ByteArray): ByteArray {
    val exifPayload = EXIF_PREFIX + tiff
    return ByteArrayOutputStream().apply {
        writeJpegStart()
        writeJpegSegment(marker = 0xE1, payload = exifPayload)
        writeJpegEnd()
    }.toByteArray()
}

private fun jpegWithoutExif(): ByteArray =
    ByteArrayOutputStream().apply {
        writeJpegStart()
        writeJpegSegment(marker = 0xE0, payload = byteArrayOf())
        writeJpegEnd()
    }.toByteArray()

private fun jpegWithEmptySegments(count: Int): ByteArray =
    ByteArrayOutputStream().apply {
        writeJpegStart()
        repeat(count) {
            writeJpegSegment(marker = 0xE0, payload = byteArrayOf())
        }
        writeJpegEnd()
    }.toByteArray()

private fun jpegWithMarkerFill(fillCount: Int): ByteArray =
    ByteArrayOutputStream().apply {
        writeJpegStart()
        repeat(fillCount) { write(0xFF) }
        write(0xD9)
    }.toByteArray()

private fun jpegBeyondLogicalScanLimit(): ByteArray =
    ByteArrayOutputStream().apply {
        writeJpegStart()
        repeat(5) {
            writeJpegSegment(marker = 0xE0, payload = ByteArray(MAX_JPEG_SEGMENT_PAYLOAD_BYTES))
        }
        writeJpegEnd()
    }.toByteArray()

private fun ByteArrayOutputStream.writeJpegStart() {
    write(0xFF)
    write(0xD8)
}

private fun ByteArrayOutputStream.writeJpegEnd() {
    write(0xFF)
    write(0xD9)
}

private fun ByteArrayOutputStream.writeJpegSegment(
    marker: Int,
    payload: ByteArray,
) {
    require(payload.size <= MAX_JPEG_SEGMENT_PAYLOAD_BYTES)
    write(0xFF)
    write(marker)
    writeUnsignedShortBigEndian(payload.size + 2)
    write(payload)
}

private fun validTiff(
    byteOrder: FixtureByteOrder,
    orientation: Int,
): ByteArray =
    ByteArrayOutputStream().apply {
        writeTiffHeader(byteOrder, ifdOffset = 8L)
        writeUnsignedShort(1, byteOrder)
        writeUnsignedShort(0x0112, byteOrder)
        writeUnsignedShort(3, byteOrder)
        writeUnsignedInt(1L, byteOrder)
        writeUnsignedShort(orientation, byteOrder)
        writeUnsignedShort(0, byteOrder)
        writeUnsignedInt(0L, byteOrder)
    }.toByteArray()

private fun tiffWithDeclaredEntryCount(entryCount: Int): ByteArray =
    ByteArrayOutputStream().apply {
        writeTiffHeader(FixtureByteOrder.LITTLE_ENDIAN, ifdOffset = 8L)
        writeUnsignedShort(entryCount, FixtureByteOrder.LITTLE_ENDIAN)
    }.toByteArray()

private fun tiffWithIfdOffset(ifdOffset: Long): ByteArray =
    ByteArrayOutputStream().apply {
        writeTiffHeader(FixtureByteOrder.LITTLE_ENDIAN, ifdOffset)
    }.toByteArray()

private fun ByteArrayOutputStream.writeTiffHeader(
    byteOrder: FixtureByteOrder,
    ifdOffset: Long,
) {
    val marker = if (byteOrder == FixtureByteOrder.LITTLE_ENDIAN) 0x49 else 0x4D
    write(marker)
    write(marker)
    writeUnsignedShort(42, byteOrder)
    writeUnsignedInt(ifdOffset, byteOrder)
}

private fun webp(chunks: List<WebpChunk>): ByteArray {
    val encodedChunks =
        ByteArrayOutputStream().apply {
            chunks.forEach { chunk ->
                require(chunk.fourCc.length == 4)
                writeAscii(chunk.fourCc)
                writeUnsignedIntLittleEndian(chunk.payload.size.toLong())
                write(chunk.payload)
                if (chunk.payload.size % 2 != 0) write(0)
            }
        }.toByteArray()

    return ByteArrayOutputStream().apply {
        writeAscii("RIFF")
        writeUnsignedIntLittleEndian(4L + encodedChunks.size)
        writeAscii("WEBP")
        write(encodedChunks)
    }.toByteArray()
}

private fun minimalWebpImageChunks(hasExif: Boolean): List<WebpChunk> =
    listOf(
        WebpChunk(
            fourCc = "VP8X",
            payload =
                byteArrayOf(
                    (if (hasExif) 0x18 else 0x10).toByte(),
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                ),
        ),
        WebpChunk(
            fourCc = "VP8L",
            payload =
                byteArrayOf(
                    0x2F,
                    0x00,
                    0x00,
                    0x00,
                    0x10,
                    0x07,
                    0xD0.toByte(),
                    0x8D.toByte(),
                    0x3A,
                    0xF4.toByte(),
                    0xA3.toByte(),
                    0xFB.toByte(),
                    0x81.toByte(),
                    0x88.toByte(),
                    0xE8.toByte(),
                    0x7F,
                    0x00,
                ),
        ),
    )

private fun webpWithForgedRiffSize(): ByteArray =
    ByteArrayOutputStream().apply {
        writeAscii("RIFF")
        writeUnsignedIntLittleEndian(UINT32_MAX)
        writeAscii("WEBP")
    }.toByteArray()

private fun webpWithForgedChunkSize(): ByteArray =
    ByteArrayOutputStream().apply {
        writeAscii("RIFF")
        writeUnsignedIntLittleEndian(12L)
        writeAscii("WEBP")
        writeAscii("JUNK")
        writeUnsignedIntLittleEndian(UINT32_MAX)
    }.toByteArray()

private fun webpWithMissingOddChunkPadding(): ByteArray =
    ByteArrayOutputStream().apply {
        writeAscii("RIFF")
        writeUnsignedIntLittleEndian(13L)
        writeAscii("WEBP")
        writeAscii("JUNK")
        writeUnsignedIntLittleEndian(1L)
        write(0)
    }.toByteArray()

private fun ByteArrayOutputStream.writeUnsignedShortBigEndian(value: Int) {
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeUnsignedShort(
    value: Int,
    byteOrder: FixtureByteOrder,
) {
    if (byteOrder == FixtureByteOrder.LITTLE_ENDIAN) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    } else {
        writeUnsignedShortBigEndian(value)
    }
}

private fun ByteArrayOutputStream.writeUnsignedIntLittleEndian(value: Long) {
    writeUnsignedInt(value, FixtureByteOrder.LITTLE_ENDIAN)
}

private fun ByteArrayOutputStream.writeUnsignedInt(
    value: Long,
    byteOrder: FixtureByteOrder,
) {
    val shifts =
        if (byteOrder == FixtureByteOrder.LITTLE_ENDIAN) {
            0..24 step 8
        } else {
            24 downTo 0 step 8
        }
    shifts.forEach { shift ->
        write(((value ushr shift) and 0xFF).toInt())
    }
}

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private data class WebpChunk(
    val fourCc: String,
    val payload: ByteArray,
)

private enum class FixtureByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN,
}

private val EXIF_PREFIX = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
private val PNG_SIGNATURE =
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private const val MAX_JPEG_SEGMENT_PAYLOAD_BYTES = 65_533
private const val WEBP_EXIF_LIMIT_BYTES = 64 * 1024
private const val UINT32_MAX = 0xFFFF_FFFFL
