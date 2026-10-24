package com.kairo.reader.data.books

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object BookTextDecoder {
    fun decode(
        bytes: ByteArray,
        declaredCharset: String? = null,
    ): String {
        if (bytes.isEmpty()) return ""

        detectBom(bytes)?.let { encoding ->
            return decodeLeniently(
                bytes = bytes.copyOfRange(encoding.signature.size, bytes.size),
                charset = encoding.charset,
            )
        }

        declaredCharset
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?.let { charset -> return decodeLeniently(bytes, charset) }

        val sample = bytes.copyOf(minOf(bytes.size, CHARSET_PROBE_BYTES))
        extractDeclaredCharset(String(sample, Charsets.ISO_8859_1))
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?.let { charset -> return decodeLeniently(bytes, charset) }

        detectUtf16Heuristic(sample)?.let { charset ->
            return decodeLeniently(bytes, charset)
        }

        return try {
            decodeStrictly(bytes, Charsets.UTF_8)
        } catch (_: CharacterCodingException) {
            decodeLeniently(bytes, WINDOWS_1252)
        }
    }

    private fun decodeStrictly(
        bytes: ByteArray,
        charset: Charset,
    ): String =
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun decodeLeniently(
        bytes: ByteArray,
        charset: Charset,
    ): String = String(bytes, charset)

    private fun detectBom(bytes: ByteArray): BomEncoding? =
        BOM_ENCODINGS.firstOrNull { encoding -> bytes.startsWith(encoding.signature) }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean =
        size >= signature.size && signature.indices.all { index -> this[index] == signature[index] }

    private fun detectUtf16Heuristic(sample: ByteArray): Charset? {
        if (sample.size < MIN_UTF16_PROBE_BYTES) return null
        var evenNulls = 0
        var oddNulls = 0
        var evenCount = 0
        var oddCount = 0
        sample.forEachIndexed { index, byte ->
            if (index % UTF16_CODE_UNIT_BYTES == 0) {
                evenCount += 1
                if (byte == 0.toByte()) evenNulls += 1
            } else {
                oddCount += 1
                if (byte == 0.toByte()) oddNulls += 1
            }
        }

        val evenRatio = evenNulls.toDouble() / evenCount.coerceAtLeast(1)
        val oddRatio = oddNulls.toDouble() / oddCount.coerceAtLeast(1)
        return when {
            evenRatio > UTF16_NULL_DOMINANT_RATIO && oddRatio < UTF16_NULL_SPARSE_RATIO -> Charsets.UTF_16BE
            oddRatio > UTF16_NULL_DOMINANT_RATIO && evenRatio < UTF16_NULL_SPARSE_RATIO -> Charsets.UTF_16LE
            else -> null
        }
    }

    private fun extractDeclaredCharset(sample: String): String? =
        XML_ENCODING.find(sample)?.groupValues?.getOrNull(1)?.trim()
            ?: HTML_CHARSET.find(sample)?.groupValues?.getOrNull(1)?.trim()

    private data class BomEncoding(val charset: Charset, val signature: ByteArray,)

    private const val CHARSET_PROBE_BYTES = 4096
    private const val MIN_UTF16_PROBE_BYTES = 4
    private const val UTF16_CODE_UNIT_BYTES = 2
    private const val UTF16_NULL_DOMINANT_RATIO = 0.3
    private const val UTF16_NULL_SPARSE_RATIO = 0.1
    private val WINDOWS_1252 = Charset.forName("windows-1252")
    private val XML_ENCODING = Regex("(?i)encoding\\s*=\\s*['\"]([^'\"]+)['\"]")
    private val HTML_CHARSET = Regex("(?i)charset\\s*=\\s*['\"]?([A-Za-z0-9_\\-]+)")

    // BOM signatures are fixed protocol representations; their raw bytes are clearest here.
    @Suppress("MagicNumber")
    private val BOM_ENCODINGS =
        listOf(
            BomEncoding(Charset.forName("UTF-32BE"), byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())),
            BomEncoding(Charset.forName("UTF-32LE"), byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)),
            BomEncoding(Charsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
            BomEncoding(Charsets.UTF_16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
            BomEncoding(Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
        )
}
