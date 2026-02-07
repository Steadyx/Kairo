package com.example.kairo.data.books.epub

import java.nio.charset.Charset

internal object EpubTextDecoder {
    fun decodeTextEntry(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        detectBomCharset(bytes)?.let { charset ->
            val bomLength =
                when (charset.name()) {
                    "UTF-8" -> 3
                    "UTF-16BE", "UTF-16LE" -> 2
                    "UTF-32BE", "UTF-32LE" -> 4
                    else -> 0
                }
            val slice = if (bomLength in 1 until bytes.size) bytes.copyOfRange(bomLength, bytes.size) else bytes
            return runCatching { String(slice, charset) }.getOrElse { String(bytes, Charsets.UTF_8) }
        }

        val sampleLength = minOf(bytes.size, 4096)
        val sample = bytes.copyOf(sampleLength)
        val asciiProbe = String(sample, Charsets.ISO_8859_1)
        val declared = extractDeclaredCharset(asciiProbe)
        if (!declared.isNullOrBlank()) {
            val declaredCharset = runCatching { Charset.forName(declared) }.getOrNull()
            if (declaredCharset != null) {
                return runCatching { String(bytes, declaredCharset) }.getOrElse { String(bytes, Charsets.UTF_8) }
            }
        }

        detectUtf16Heuristic(sample)?.let { charset ->
            return runCatching { String(bytes, charset) }.getOrElse { String(bytes, Charsets.UTF_8) }
        }

        return runCatching { String(bytes, Charsets.UTF_8) }.getOrElse {
            runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrElse { String(bytes) }
        }
    }

    private fun detectBomCharset(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return Charsets.UTF_16BE
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() &&
            bytes[1] == 0x00.toByte() &&
            bytes[2] == 0xFE.toByte() &&
            bytes[3] == 0xFF.toByte()
        ) {
            return Charset.forName("UTF-32BE")
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte() &&
            bytes[2] == 0x00.toByte() &&
            bytes[3] == 0x00.toByte()
        ) {
            return Charset.forName("UTF-32LE")
        }
        return null
    }

    private fun detectUtf16Heuristic(sample: ByteArray): Charset? {
        if (sample.size < 4) return null
        var evenNulls = 0
        var oddNulls = 0
        var evenCount = 0
        var oddCount = 0

        for (i in sample.indices) {
            if (i % 2 == 0) {
                evenCount++
                if (sample[i] == 0.toByte()) evenNulls++
            } else {
                oddCount++
                if (sample[i] == 0.toByte()) oddNulls++
            }
        }

        val evenRatio = if (evenCount == 0) 0.0 else evenNulls.toDouble() / evenCount.toDouble()
        val oddRatio = if (oddCount == 0) 0.0 else oddNulls.toDouble() / oddCount.toDouble()

        return when {
            evenRatio > 0.3 && oddRatio < 0.1 -> Charsets.UTF_16BE
            oddRatio > 0.3 && evenRatio < 0.1 -> Charsets.UTF_16LE
            else -> null
        }
    }

    private fun extractDeclaredCharset(sample: String): String? {
        val declarationRegex = Regex("(?i)encoding\\s*=\\s*['\"]([^'\"]+)['\"]")
        val declarationMatch = declarationRegex.find(sample)
        if (declarationMatch != null) {
            return declarationMatch.groupValues.getOrNull(1)?.trim()
        }

        val metaCharsetRegex = Regex("(?i)charset\\s*=\\s*([A-Za-z0-9_\\-]+)")
        val metaMatch = metaCharsetRegex.find(sample)
        return metaMatch?.groupValues?.getOrNull(1)?.trim()
    }
}
