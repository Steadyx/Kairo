package com.kairo.reader.data.books

import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

internal object ImportFingerprint {
    fun sourceFingerprint(
        extension: String,
        input: InputStream,
        maxBytes: Long = MAX_SOURCE_BYTES,
        checkActive: () -> Unit = {},
    ): String {
        val normalizedExtension = extension.lowercase(Locale.ROOT)
        return "source:$normalizedExtension:${input.sha256Hex(maxBytes = maxBytes, checkActive = checkActive)}"
    }

    fun sourceFingerprint(
        extension: String,
        input: InputStream,
        copyTo: OutputStream,
        maxBytes: Long = MAX_SOURCE_BYTES,
        checkActive: () -> Unit = {},
    ): String {
        val normalizedExtension = extension.lowercase(Locale.ROOT)
        return "source:$normalizedExtension:${input.sha256Hex(copyTo, maxBytes, checkActive)}"
    }

    fun withSourceExtension(
        fingerprint: String,
        extension: String,
    ): String {
        val digest = fingerprint.substringAfterLast(':')
        require(fingerprint.startsWith(SOURCE_PREFIX) && digest.length == SHA_256_HEX_LENGTH) {
            "Invalid source fingerprint"
        }
        return "$SOURCE_PREFIX${extension.lowercase(Locale.ROOT)}:$digest"
    }

    fun webUrlFingerprint(normalizedUrl: String): String =
        "web:${normalizeContent(normalizedUrl)}".toByteArray(Charsets.UTF_8).sha256Hex().let {
            "web:url:$it"
        }

    fun textFingerprint(normalizedText: String): String =
        normalizeContent(normalizedText).toByteArray(Charsets.UTF_8).sha256Hex().let {
            "text:sha256:$it"
        }

    fun contentFingerprint(book: Book): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateValue(normalizeMetadata(book.title))
        digest.updateValue(book.authors.joinToString("|") { normalizeMetadata(it) })
        digest.updateValue(book.chapters.size.toString())
        book.chapters
            .sortedBy { it.index }
            .forEachIndexed { index, chapter ->
                digest.updateValue(index.toString())
                digest.updateValue(normalizeMetadata(chapter.title.orEmpty()))
                digest.updateValue(normalizeContent(chapter.plainText))
            }
        digest.updateValue(book.tableOfContents.size.toString())
        book.tableOfContents.forEachIndexed { index, entry ->
            digest.updateValue(index.toString())
            digest.updateValue(normalizeMetadata(entry.label))
            digest.updateValue(entry.depth.toString())
            digest.updateValue(entry.target?.chapterIndex?.toString().orEmpty())
            digest.updateValue(entry.target?.characterOffset?.toString().orEmpty())
        }
        return "content:sha256:${digest.hexDigest()}"
    }

    fun bookIdForFingerprint(fingerprint: String): BookId =
        BookId("imported-${fingerprint.toByteArray(Charsets.UTF_8).sha256Hex()}")

    private fun normalizeMetadata(value: String): String =
        value
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)

    private fun normalizeContent(value: String): String =
        value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

    private fun InputStream.sha256Hex(
        copyTo: OutputStream? = null,
        maxBytes: Long,
        checkActive: () -> Unit,
    ): String {
        require(maxBytes >= 0L)
        var totalBytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            checkActive()
            val read = read(buffer)
            if (read == -1) break
            if (read > 0) {
                totalBytes += read
                if (totalBytes > maxBytes) throw ImportSourceTooLargeException(maxBytes)
                digest.update(buffer, 0, read)
                copyTo?.write(buffer, 0, read)
            }
        }
        return digest.hexDigest()
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(this)
        return digest.hexDigest()
    }

    private fun MessageDigest.updateValue(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        update(0.toByte())
        update(bytes)
        update(0.toByte())
    }

    private fun MessageDigest.hexDigest(): String =
        digest().joinToString(separator = "") { "%02x".format(it.toInt() and BYTE_MASK) }

    private val WHITESPACE = Regex("\\s+")
    private const val SOURCE_PREFIX = "source:"
    private const val SHA_256_HEX_LENGTH = 64
    private const val MAX_SOURCE_BYTES = 256L * 1024L * 1024L
    private const val BUFFER_SIZE = 64 * 1024
    private const val BYTE_MASK = 0xFF
}

internal class ImportSourceTooLargeException(maxBytes: Long) :
    IllegalArgumentException(
        "File is too large to import (maximum ${maxBytes / SOURCE_BYTES_PER_MEBIBYTE} MB)",
    )

private const val SOURCE_BYTES_PER_MEBIBYTE = 1024L * 1024L
