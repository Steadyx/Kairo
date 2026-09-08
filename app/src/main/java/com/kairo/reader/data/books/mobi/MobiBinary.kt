package com.kairo.reader.data.books.mobi

import java.nio.charset.Charset

internal object MobiBinary {
    fun readInt(data: ByteArray, offset: Int): Int {
        if (offset + INT_BYTES > data.size || offset < 0) return 0
        return ((data[offset].toInt() and BYTE_MASK) shl MOST_SIGNIFICANT_BYTE_SHIFT) or
            ((data[offset + 1].toInt() and BYTE_MASK) shl SECOND_BYTE_SHIFT) or
            ((data[offset + 2].toInt() and BYTE_MASK) shl THIRD_BYTE_SHIFT) or
            (data[offset + FOURTH_BYTE_OFFSET].toInt() and BYTE_MASK)
    }

    fun readLittleEndianShort(data: ByteArray, offset: Int): Int {
        if (offset + SHORT_BYTES > data.size || offset < 0) return 0
        return (data[offset].toInt() and BYTE_MASK) or
            ((data[offset + 1].toInt() and BYTE_MASK) shl THIRD_BYTE_SHIFT)
    }

    fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        if (offset + INT_BYTES > data.size || offset < 0) return 0
        return (data[offset].toInt() and BYTE_MASK) or
            ((data[offset + 1].toInt() and BYTE_MASK) shl THIRD_BYTE_SHIFT) or
            ((data[offset + 2].toInt() and BYTE_MASK) shl SECOND_BYTE_SHIFT) or
            ((data[offset + FOURTH_BYTE_OFFSET].toInt() and BYTE_MASK) shl MOST_SIGNIFICANT_BYTE_SHIFT)
    }

    fun parseRecordOffsets(
        data: ByteArray,
        recordCount: Int,
    ): List<Int> {
        val offsets = ArrayList<Int>(recordCount)
        var cursor = PALM_DATABASE_RECORD_TABLE_OFFSET
        repeat(recordCount) {
            if (cursor + PALM_DATABASE_RECORD_INFO_SIZE > data.size) return@repeat
            offsets.add(readInt(data, cursor))
            cursor += PALM_DATABASE_RECORD_INFO_SIZE
        }
        return offsets
    }

    fun detectImageType(bytes: ByteArray): MobiImageType? {
        if (bytes.size < MIN_IMAGE_HEADER_BYTES) return null
        return when {
            bytes.hasSignature(JPEG_SIGNATURE) -> MobiImageType("jpg")
            bytes.hasSignature(PNG_SIGNATURE) -> MobiImageType("png")
            bytes.hasSignature(GIF_SIGNATURE) -> MobiImageType("gif")
            bytes.hasSignature(RIFF_SIGNATURE) &&
                bytes.hasSignature(WEBP_SIGNATURE, WEBP_SIGNATURE_OFFSET) -> MobiImageType("webp")
            bytes.hasSignature(BMP_SIGNATURE) -> MobiImageType("bmp")
            else -> null
        }
    }

    fun isImageRecord(
        data: ByteArray,
        recordOffsets: List<Int>,
        index: Int,
    ): Boolean {
        if (index !in recordOffsets.indices) return false
        val start = recordOffsets[index]
        val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
        if (start < 0 || end > data.size || end <= start) return false
        val headerEnd = (start + IMAGE_PROBE_BYTES).coerceAtMost(end)
        if (headerEnd - start < MIN_IMAGE_HEADER_BYTES) return false
        return detectImageType(data.copyOfRange(start, headerEnd)) != null
    }

    fun findFirstImageRecordIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
    ): Int? {
        if (recordOffsets.isEmpty()) return null
        for (index in 1..recordOffsets.lastIndex) {
            if (isImageRecord(data, recordOffsets, index)) return index
        }
        return null
    }

    fun looksMostlyBinary(data: ByteArray, charset: Charset = Charsets.UTF_8): Boolean {
        val text = decodeText(data, charset)
        if (text.isEmpty()) return true
        val printable = text.count { it != '\uFFFD' && (!it.isISOControl() || it.isWhitespace()) }
        return printable.toDouble() / text.length < MIN_PRINTABLE_TEXT_RATIO
    }

    fun decompressPalmDoc(
        data: ByteArray,
        maxOutputBytes: Int = MobiLimits.MAX_TEXT_RECORD_BYTES,
        checkActive: () -> Unit = {},
    ): ByteArray {
        checkActive()
        val output = MobiTextBuffer(maxOutputBytes)
        var lastCancellationCheck = 0
        var i = 0

        while (i < data.size) {
            if (i - lastCancellationCheck >= CANCELLATION_CHECK_INTERVAL) {
                checkActive()
                lastCancellationCheck = i
            }
            val byte = data[i].toInt() and BYTE_MASK
            i++
            when (byte) {
                0 -> output.append(0)
                in PALMDOC_LITERAL_RUN_RANGE -> {
                    repeat(byte) {
                        if (i < data.size) {
                            output.append(data[i])
                            i++
                        }
                    }
                }
                in PALMDOC_DIRECT_BYTE_RANGE -> output.append(byte.toByte())
                in PALMDOC_BACK_REFERENCE_RANGE -> {
                    if (i < data.size) {
                        val next = data[i].toInt() and BYTE_MASK
                        i++
                        val distance =
                            ((byte shl THIRD_BYTE_SHIFT) or next) shr PALMDOC_LENGTH_BITS and
                                PALMDOC_DISTANCE_MASK
                        val length = (next and PALMDOC_LENGTH_MASK) + PALMDOC_MIN_MATCH_LENGTH
                        val position = output.size() - distance
                        if (position >= 0) {
                            repeat(length) { offset ->
                                val source = position + offset
                                if (source in 0 until output.size()) {
                                    output.append(output[source])
                                }
                            }
                        }
                    }
                }
                else -> {
                    output.append(' '.code.toByte())
                    output.append((byte xor PALMDOC_SPACE_XOR_MASK).toByte())
                }
            }
        }
        checkActive()
        return output.toByteArray()
    }

    fun decodeText(
        bytes: ByteArray,
        charset: Charset,
    ): String = String(bytes, charset)

    fun resolveCharset(encoding: Int): Charset =
        when (encoding) {
            MOBI_ENCODING_UTF_8 -> Charsets.UTF_8
            MOBI_ENCODING_WINDOWS_1252 ->
                runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.UTF_8)
            else -> Charsets.UTF_8
        }

    private fun ByteArray.hasSignature(signature: ByteArray, offset: Int = 0): Boolean =
        offset >= 0 &&
            offset + signature.size <= size &&
            signature.indices.all { signatureIndex ->
                this[offset + signatureIndex] == signature[signatureIndex]
            }

    private const val BYTE_MASK = 0xFF
    private const val SHORT_BYTES = 2
    private const val INT_BYTES = 4
    private const val FOURTH_BYTE_OFFSET = 3
    private const val THIRD_BYTE_SHIFT = 8
    private const val SECOND_BYTE_SHIFT = 16
    private const val MOST_SIGNIFICANT_BYTE_SHIFT = 24

    // Palm Database record list starts after the 78-byte database header.
    private const val PALM_DATABASE_RECORD_TABLE_OFFSET = 78
    private const val PALM_DATABASE_RECORD_INFO_SIZE = 8

    private const val MIN_IMAGE_HEADER_BYTES = 12
    private const val IMAGE_PROBE_BYTES = 32
    private const val WEBP_SIGNATURE_OFFSET = 8

    private const val MIN_PRINTABLE_TEXT_RATIO = 0.6

    private const val CANCELLATION_CHECK_INTERVAL = 1024
    private val PALMDOC_LITERAL_RUN_RANGE = 1..8
    private val PALMDOC_DIRECT_BYTE_RANGE = 9..0x7F
    private val PALMDOC_BACK_REFERENCE_RANGE = 0x80..0xBF
    private const val PALMDOC_LENGTH_BITS = 3
    private const val PALMDOC_DISTANCE_MASK = 0x7FF
    private const val PALMDOC_LENGTH_MASK = 0x07
    private const val PALMDOC_MIN_MATCH_LENGTH = 3
    private const val PALMDOC_SPACE_XOR_MASK = 0x80

    private const val MOBI_ENCODING_UTF_8 = 65001
    private const val MOBI_ENCODING_WINDOWS_1252 = 1252

    // Image signatures are protocol data; naming the byte sequence documents its format.
    @Suppress("MagicNumber")
    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

    @Suppress("MagicNumber")
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Suppress("MagicNumber")
    private val GIF_SIGNATURE = byteArrayOf(0x47, 0x49, 0x46)

    @Suppress("MagicNumber")
    private val RIFF_SIGNATURE = byteArrayOf(0x52, 0x49, 0x46, 0x46)

    @Suppress("MagicNumber")
    private val WEBP_SIGNATURE = byteArrayOf(0x57, 0x45, 0x42, 0x50)

    @Suppress("MagicNumber")
    private val BMP_SIGNATURE = byteArrayOf(0x42, 0x4D)
}
