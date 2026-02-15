package com.example.kairo.data.books.mobi

import java.nio.charset.Charset

internal object MobiBinary {
    fun readInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size || offset < 0) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    fun readLittleEndianShort(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size || offset < 0) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size || offset < 0) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun parseRecordOffsets(
        data: ByteArray,
        recordCount: Int,
    ): List<Int> {
        val offsets = ArrayList<Int>(recordCount)
        var cursor = 78
        repeat(recordCount) {
            if (cursor + 8 > data.size) return@repeat
            offsets.add(readInt(data, cursor))
            cursor += 8
        }
        return offsets
    }

    fun detectImageType(bytes: ByteArray): MobiImageType? {
        if (bytes.size < 12) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> MobiImageType("jpg")
            bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> MobiImageType("png")
            bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> MobiImageType("gif")
            bytes[0] == 0x52.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() &&
                bytes[11] == 0x50.toByte() -> MobiImageType("webp")
            bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> MobiImageType("bmp")
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
        val headerEnd = (start + 32).coerceAtMost(end)
        if (headerEnd - start < 12) return false
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

    fun looksMostlyBinary(data: ByteArray): Boolean {
        if (data.isEmpty()) return true
        var printable = 0
        data.forEach { byte ->
            val value = byte.toInt() and 0xFF
            if (value == 0x09 || value == 0x0A || value == 0x0D ||
                value in 0x20..0x7E || value >= 0xC0
            ) {
                printable++
            }
        }
        return printable.toDouble() / data.size.toDouble() < 0.6
    }

    fun decompressPalmDoc(data: ByteArray): ByteArray {
        val output = ArrayList<Byte>(data.size * 2)
        var i = 0

        while (i < data.size) {
            val byte = data[i].toInt() and 0xFF
            i++
            when (byte) {
                0 -> output.add(0)
                in 1..8 -> {
                    repeat(byte) {
                        if (i < data.size) {
                            output.add(data[i])
                            i++
                        }
                    }
                }
                in 9..0x7F -> output.add(byte.toByte())
                in 0x80..0xBF -> {
                    if (i < data.size) {
                        val next = data[i].toInt() and 0xFF
                        i++
                        val distance = ((byte shl 8) or next) shr 3 and 0x7FF
                        val length = (next and 0x07) + 3
                        val position = output.size - distance
                        if (position >= 0) {
                            repeat(length) { offset ->
                                val source = position + offset
                                if (source in output.indices) {
                                    output.add(output[source])
                                }
                            }
                        }
                    }
                }
                else -> {
                    output.add(' '.code.toByte())
                    output.add((byte xor 0x80).toByte())
                }
            }
        }
        return output.toByteArray()
    }

    fun decodeText(
        bytes: ByteArray,
        charset: Charset,
    ): String = runCatching { String(bytes, charset) }.getOrDefault(String(bytes))

    fun resolveCharset(encoding: Int): Charset =
        when (encoding) {
            65001 -> Charsets.UTF_8
            1252 -> runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.UTF_8)
            else -> Charsets.UTF_8
        }
}
