@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.kairo.reader.ui.reader

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

internal enum class ReaderImageOrientation {
    NORMAL,
    SWAP_DIMENSIONS,
    UNAVAILABLE,
}

/** Reads only bounded container metadata; bitmap pixels and linked TIFF data are never loaded. */
internal fun readReaderImageOrientation(
    imageFile: File,
    mimeType: String?,
): ReaderImageOrientation =
    when {
        mimeType == "image/jpeg" || mimeType == "image/jpg" ->
            readBoundedOrientation(
                imageFile = imageFile,
                maximumAddressBytes = MAX_JPEG_HEADER_SCAN_BYTES,
                readOrientation = ::readJpegOrientation,
            )
        mimeType == "image/webp" ->
            readBoundedOrientation(
                imageFile = imageFile,
                maximumAddressBytes = Long.MAX_VALUE,
                readOrientation = ::readWebpOrientation,
            )
        mimeType != null && mimeType in RAW_BOUNDS_MIME_TYPES -> ReaderImageOrientation.NORMAL
        else -> ReaderImageOrientation.UNAVAILABLE
    }

internal fun readerImageOrientationFromExifValue(value: Int): ReaderImageOrientation =
    when (value) {
        in 1..4 -> ReaderImageOrientation.NORMAL
        in 5..8 -> ReaderImageOrientation.SWAP_DIMENSIONS
        else -> ReaderImageOrientation.UNAVAILABLE
    }

internal fun orientReaderImageBounds(
    width: Int,
    height: Int,
    orientation: ReaderImageOrientation,
): ReaderImageSize? {
    if (width <= 0 || height <= 0) return null
    return when (orientation) {
        ReaderImageOrientation.NORMAL ->
            ReaderImageSize(widthPx = width.toFloat(), heightPx = height.toFloat())
        ReaderImageOrientation.SWAP_DIMENSIONS ->
            ReaderImageSize(widthPx = height.toFloat(), heightPx = width.toFloat())
        ReaderImageOrientation.UNAVAILABLE -> null
    }
}

private fun readBoundedOrientation(
    imageFile: File,
    maximumAddressBytes: Long,
    readOrientation: (BoundedRandomAccessReader) -> ReaderImageOrientation,
): ReaderImageOrientation =
    try {
        RandomAccessFile(imageFile, "r").use { input ->
            readOrientation(
                BoundedRandomAccessReader(
                    input = input,
                    maximumAddressBytes = maximumAddressBytes,
                ),
            )
        }
    } catch (_: IOException) {
        ReaderImageOrientation.UNAVAILABLE
    } catch (_: SecurityException) {
        ReaderImageOrientation.UNAVAILABLE
    }

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun readJpegOrientation(reader: BoundedRandomAccessReader): ReaderImageOrientation {
    if (reader.readUnsignedByte(0) != JPEG_MARKER_PREFIX ||
        reader.readUnsignedByte(1) != JPEG_START_OF_IMAGE
    ) {
        return ReaderImageOrientation.UNAVAILABLE
    }

    var position = JPEG_SIGNATURE_BYTES
    var markerCount = 0
    var resolvedOrientation: ReaderImageOrientation? = null
    while (position < reader.addressLimit) {
        if (reader.readUnsignedByte(position) != JPEG_MARKER_PREFIX) {
            return ReaderImageOrientation.UNAVAILABLE
        }
        var markerFillCount = 0
        while (reader.readUnsignedByte(position) == JPEG_MARKER_PREFIX) {
            markerFillCount += 1
            if (markerFillCount > MAX_JPEG_MARKER_FILL_BYTES) {
                return ReaderImageOrientation.UNAVAILABLE
            }
            position += 1
            if (position >= reader.addressLimit) return ReaderImageOrientation.UNAVAILABLE
        }

        val marker = reader.readUnsignedByte(position) ?: return ReaderImageOrientation.UNAVAILABLE
        position += 1
        if (marker == JPEG_STUFFED_BYTE) return ReaderImageOrientation.UNAVAILABLE
        markerCount += 1
        if (markerCount > MAX_CONTAINER_ENTRIES) return ReaderImageOrientation.UNAVAILABLE

        when {
            marker == JPEG_END_OF_IMAGE ->
                return resolvedOrientation ?: ReaderImageOrientation.NORMAL
            marker == JPEG_START_OF_SCAN -> {
                readJpegSegmentEnd(reader, position) ?: return ReaderImageOrientation.UNAVAILABLE
                return resolvedOrientation ?: ReaderImageOrientation.NORMAL
            }
            marker == JPEG_START_OF_IMAGE -> return ReaderImageOrientation.UNAVAILABLE
            marker == JPEG_TEMPORARY || marker in JPEG_RESTART_MARKERS -> continue
        }

        val segmentLength = reader.readUnsignedShortBigEndian(position) ?: return ReaderImageOrientation.UNAVAILABLE
        if (segmentLength < JPEG_SEGMENT_LENGTH_BYTES) return ReaderImageOrientation.UNAVAILABLE
        val segmentEnd = reader.checkedEnd(position, segmentLength.toLong()) ?: return ReaderImageOrientation.UNAVAILABLE
        val payloadStart = position + JPEG_SEGMENT_LENGTH_BYTES
        val payloadLength = segmentLength.toLong() - JPEG_SEGMENT_LENGTH_BYTES

        if (marker == JPEG_APP1 &&
            payloadLength >= JPEG_EXIF_HEADER_BYTES &&
            reader.matches(payloadStart, EXIF_IDENTIFIER, segmentEnd) &&
            reader.readUnsignedByte(payloadStart + EXIF_IDENTIFIER.length) == 0 &&
            reader.readUnsignedByte(payloadStart + EXIF_IDENTIFIER.length + 1) == 0
        ) {
            val tiffStart = payloadStart + JPEG_EXIF_HEADER_BYTES
            val tiffLength = payloadLength - JPEG_EXIF_HEADER_BYTES
            val orientation = readTiffOrientation(reader, tiffStart, tiffLength)
            if (orientation == ReaderImageOrientation.UNAVAILABLE) return orientation
            if (resolvedOrientation == null) resolvedOrientation = orientation
        }
        position = segmentEnd
    }
    return ReaderImageOrientation.UNAVAILABLE
}

private fun readJpegSegmentEnd(
    reader: BoundedRandomAccessReader,
    lengthOffset: Long,
): Long? {
    val segmentLength = reader.readUnsignedShortBigEndian(lengthOffset) ?: return null
    if (segmentLength < JPEG_SEGMENT_LENGTH_BYTES) return null
    return reader.checkedEnd(lengthOffset, segmentLength.toLong())
}

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun readWebpOrientation(reader: BoundedRandomAccessReader): ReaderImageOrientation {
    if (!reader.matches(0, RIFF_IDENTIFIER) || !reader.matches(WEBP_IDENTIFIER_OFFSET, WEBP_IDENTIFIER)) {
        return ReaderImageOrientation.UNAVAILABLE
    }
    val riffSize = reader.readUnsignedIntLittleEndian(RIFF_SIZE_OFFSET) ?: return ReaderImageOrientation.UNAVAILABLE
    if (riffSize < WEBP_IDENTIFIER.length.toLong()) return ReaderImageOrientation.UNAVAILABLE
    val riffEnd = reader.checkedEnd(RIFF_CONTENT_OFFSET, riffSize) ?: return ReaderImageOrientation.UNAVAILABLE
    if (riffEnd < WEBP_CHUNKS_OFFSET) return ReaderImageOrientation.UNAVAILABLE

    var position = WEBP_CHUNKS_OFFSET
    var chunkCount = 0
    var resolvedOrientation: ReaderImageOrientation? = null
    while (position < riffEnd) {
        chunkCount += 1
        if (chunkCount > MAX_CONTAINER_ENTRIES) return ReaderImageOrientation.UNAVAILABLE
        val dataStart = reader.checkedEnd(position, WEBP_CHUNK_HEADER_BYTES, riffEnd)
            ?: return ReaderImageOrientation.UNAVAILABLE
        val chunkSize = reader.readUnsignedIntLittleEndian(position + WEBP_CHUNK_SIZE_OFFSET, riffEnd)
            ?: return ReaderImageOrientation.UNAVAILABLE
        val isExif = reader.matches(position, WEBP_EXIF_IDENTIFIER, dataStart)
        if (isExif && chunkSize > MAX_WEBP_EXIF_CHUNK_BYTES) {
            return ReaderImageOrientation.UNAVAILABLE
        }
        val dataEnd = reader.checkedEnd(dataStart, chunkSize, riffEnd)
            ?: return ReaderImageOrientation.UNAVAILABLE
        val paddedEnd = reader.checkedEnd(dataEnd, chunkSize and 1L, riffEnd)
            ?: return ReaderImageOrientation.UNAVAILABLE

        if (isExif) {
            val hasExifPrefix =
                chunkSize >= WEBP_EXIF_PREFIX_BYTES &&
                    reader.matches(dataStart, EXIF_IDENTIFIER, dataEnd) &&
                    reader.readUnsignedByte(dataStart + EXIF_IDENTIFIER.length, dataEnd) == 0 &&
                    reader.readUnsignedByte(dataStart + EXIF_IDENTIFIER.length + 1, dataEnd) == 0
            val tiffStart = if (hasExifPrefix) dataStart + WEBP_EXIF_PREFIX_BYTES else dataStart
            val tiffLength = if (hasExifPrefix) chunkSize - WEBP_EXIF_PREFIX_BYTES else chunkSize
            val orientation = readTiffOrientation(reader, tiffStart, tiffLength)
            if (orientation == ReaderImageOrientation.UNAVAILABLE) return orientation
            if (resolvedOrientation == null) resolvedOrientation = orientation
        }
        position = paddedEnd
    }
    return if (position == riffEnd) {
        resolvedOrientation ?: ReaderImageOrientation.NORMAL
    } else {
        ReaderImageOrientation.UNAVAILABLE
    }
}

@Suppress("ReturnCount")
private fun readTiffOrientation(
    reader: BoundedRandomAccessReader,
    tiffStart: Long,
    tiffLength: Long,
): ReaderImageOrientation {
    val tiffEnd = reader.checkedEnd(tiffStart, tiffLength) ?: return ReaderImageOrientation.UNAVAILABLE
    if (tiffLength < MIN_TIFF_BYTES) return ReaderImageOrientation.UNAVAILABLE
    val byteOrder =
        when {
            reader.readUnsignedByte(tiffStart) == TIFF_LITTLE_ENDIAN_BYTE &&
                reader.readUnsignedByte(tiffStart + 1) == TIFF_LITTLE_ENDIAN_BYTE -> TiffByteOrder.LITTLE_ENDIAN
            reader.readUnsignedByte(tiffStart) == TIFF_BIG_ENDIAN_BYTE &&
                reader.readUnsignedByte(tiffStart + 1) == TIFF_BIG_ENDIAN_BYTE -> TiffByteOrder.BIG_ENDIAN
            else -> return ReaderImageOrientation.UNAVAILABLE
        }
    if (reader.readUnsignedShort(tiffStart + TIFF_MAGIC_OFFSET, byteOrder, tiffEnd) != TIFF_MAGIC) {
        return ReaderImageOrientation.UNAVAILABLE
    }

    val ifdOffset = reader.readUnsignedInt(tiffStart + TIFF_IFD_OFFSET, byteOrder, tiffEnd)
        ?: return ReaderImageOrientation.UNAVAILABLE
    if (ifdOffset < TIFF_HEADER_BYTES) return ReaderImageOrientation.UNAVAILABLE
    val ifdStart = reader.checkedEnd(tiffStart, ifdOffset, tiffEnd)
        ?: return ReaderImageOrientation.UNAVAILABLE
    val entryCount = reader.readUnsignedShort(ifdStart, byteOrder, tiffEnd)
        ?: return ReaderImageOrientation.UNAVAILABLE
    if (entryCount > MAX_TIFF_IFD_ENTRIES) return ReaderImageOrientation.UNAVAILABLE
    val entriesStart = reader.checkedEnd(ifdStart, TIFF_ENTRY_COUNT_BYTES, tiffEnd)
        ?: return ReaderImageOrientation.UNAVAILABLE
    val entriesBytes = entryCount.toLong() * TIFF_ENTRY_BYTES
    val entriesEnd = reader.checkedEnd(entriesStart, entriesBytes, tiffEnd)
        ?: return ReaderImageOrientation.UNAVAILABLE
    if (reader.checkedEnd(entriesEnd, TIFF_NEXT_IFD_BYTES, tiffEnd) == null) {
        return ReaderImageOrientation.UNAVAILABLE
    }

    repeat(entryCount) { index ->
        val entryOffset = entriesStart + index.toLong() * TIFF_ENTRY_BYTES
        val tag = reader.readUnsignedShort(entryOffset, byteOrder, entriesEnd)
            ?: return ReaderImageOrientation.UNAVAILABLE
        if (tag == TIFF_ORIENTATION_TAG) {
            val type = reader.readUnsignedShort(entryOffset + TIFF_ENTRY_TYPE_OFFSET, byteOrder, entriesEnd)
                ?: return ReaderImageOrientation.UNAVAILABLE
            val count = reader.readUnsignedInt(entryOffset + TIFF_ENTRY_COUNT_OFFSET, byteOrder, entriesEnd)
                ?: return ReaderImageOrientation.UNAVAILABLE
            if (type != TIFF_SHORT_TYPE || count != 1L) return ReaderImageOrientation.UNAVAILABLE
            val value = reader.readUnsignedShort(entryOffset + TIFF_ENTRY_VALUE_OFFSET, byteOrder, entriesEnd)
                ?: return ReaderImageOrientation.UNAVAILABLE
            return readerImageOrientationFromExifValue(value)
        }
    }
    return ReaderImageOrientation.NORMAL
}

private enum class TiffByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN,
}

private class BoundedRandomAccessReader(
    private val input: RandomAccessFile,
    maximumAddressBytes: Long,
) {
    private val fileLength = input.length()
    val addressLimit: Long = minOf(fileLength, maximumAddressBytes.coerceAtLeast(0L))

    fun checkedEnd(
        start: Long,
        length: Long,
        containerEnd: Long = addressLimit,
    ): Long? {
        val limit = minOf(containerEnd, addressLimit)
        if (start < 0L || length < 0L || start > limit || length > limit - start) return null
        return start + length
    }

    fun readUnsignedByte(
        offset: Long,
        containerEnd: Long = addressLimit,
    ): Int? {
        checkedEnd(offset, 1, containerEnd) ?: return null
        input.seek(offset)
        return input.readUnsignedByte()
    }

    fun readUnsignedShortBigEndian(
        offset: Long,
        containerEnd: Long = addressLimit,
    ): Int? {
        val first = readUnsignedByte(offset, containerEnd) ?: return null
        val second = readUnsignedByte(offset + 1, containerEnd) ?: return null
        return (first shl 8) or second
    }

    fun readUnsignedShort(
        offset: Long,
        byteOrder: TiffByteOrder,
        containerEnd: Long = addressLimit,
    ): Int? {
        val first = readUnsignedByte(offset, containerEnd) ?: return null
        val second = readUnsignedByte(offset + 1, containerEnd) ?: return null
        return if (byteOrder == TiffByteOrder.LITTLE_ENDIAN) {
            first or (second shl 8)
        } else {
            (first shl 8) or second
        }
    }

    fun readUnsignedIntLittleEndian(
        offset: Long,
        containerEnd: Long = addressLimit,
    ): Long? = readUnsignedInt(offset, TiffByteOrder.LITTLE_ENDIAN, containerEnd)

    fun readUnsignedInt(
        offset: Long,
        byteOrder: TiffByteOrder,
        containerEnd: Long = addressLimit,
    ): Long? {
        val first = readUnsignedByte(offset, containerEnd)?.toLong() ?: return null
        val second = readUnsignedByte(offset + 1, containerEnd)?.toLong() ?: return null
        val third = readUnsignedByte(offset + 2, containerEnd)?.toLong() ?: return null
        val fourth = readUnsignedByte(offset + 3, containerEnd)?.toLong() ?: return null
        return if (byteOrder == TiffByteOrder.LITTLE_ENDIAN) {
            first or (second shl 8) or (third shl 16) or (fourth shl 24)
        } else {
            (first shl 24) or (second shl 16) or (third shl 8) or fourth
        }
    }

    fun matches(
        offset: Long,
        expected: String,
        containerEnd: Long = addressLimit,
    ): Boolean {
        checkedEnd(offset, expected.length.toLong(), containerEnd) ?: return false
        expected.forEachIndexed { index, expectedCharacter ->
            if (readUnsignedByte(offset + index, containerEnd) != expectedCharacter.code) return false
        }
        return true
    }
}

private const val MAX_JPEG_HEADER_SCAN_BYTES = 256L * 1024L
private const val MAX_JPEG_MARKER_FILL_BYTES = 32
private const val MAX_CONTAINER_ENTRIES = 128
private const val MAX_TIFF_IFD_ENTRIES = 256
private const val MAX_WEBP_EXIF_CHUNK_BYTES = 64L * 1024L

private const val JPEG_MARKER_PREFIX = 0xFF
private const val JPEG_START_OF_IMAGE = 0xD8
private const val JPEG_END_OF_IMAGE = 0xD9
private const val JPEG_START_OF_SCAN = 0xDA
private const val JPEG_APP1 = 0xE1
private const val JPEG_TEMPORARY = 0x01
private const val JPEG_STUFFED_BYTE = 0x00
private const val JPEG_SIGNATURE_BYTES = 2L
private const val JPEG_SEGMENT_LENGTH_BYTES = 2
private const val JPEG_EXIF_HEADER_BYTES = 6L
private val JPEG_RESTART_MARKERS = 0xD0..0xD7
private const val EXIF_IDENTIFIER = "Exif"

private const val RIFF_IDENTIFIER = "RIFF"
private const val RIFF_SIZE_OFFSET = 4L
private const val RIFF_CONTENT_OFFSET = 8L
private const val WEBP_IDENTIFIER_OFFSET = 8L
private const val WEBP_IDENTIFIER = "WEBP"
private const val WEBP_CHUNKS_OFFSET = 12L
private const val WEBP_CHUNK_HEADER_BYTES = 8L
private const val WEBP_CHUNK_SIZE_OFFSET = 4L
private const val WEBP_EXIF_IDENTIFIER = "EXIF"
private const val WEBP_EXIF_PREFIX_BYTES = 6L

private const val TIFF_LITTLE_ENDIAN_BYTE = 0x49
private const val TIFF_BIG_ENDIAN_BYTE = 0x4D
private const val TIFF_MAGIC_OFFSET = 2L
private const val TIFF_IFD_OFFSET = 4L
private const val TIFF_MAGIC = 42
private const val TIFF_HEADER_BYTES = 8L
private const val TIFF_ENTRY_COUNT_BYTES = 2L
private const val TIFF_ENTRY_BYTES = 12L
private const val TIFF_NEXT_IFD_BYTES = 4L
private const val TIFF_ORIENTATION_TAG = 0x0112
private const val TIFF_SHORT_TYPE = 3
private const val TIFF_ENTRY_TYPE_OFFSET = 2L
private const val TIFF_ENTRY_COUNT_OFFSET = 4L
private const val TIFF_ENTRY_VALUE_OFFSET = 8L
private const val MIN_TIFF_BYTES = TIFF_HEADER_BYTES + TIFF_ENTRY_COUNT_BYTES + TIFF_NEXT_IFD_BYTES

private val RAW_BOUNDS_MIME_TYPES =
    setOf(
        "image/png",
        "image/gif",
        "image/bmp",
        "image/x-ms-bmp",
        "image/vnd.wap.wbmp",
        "image/x-icon",
        "image/vnd.microsoft.icon",
    )
