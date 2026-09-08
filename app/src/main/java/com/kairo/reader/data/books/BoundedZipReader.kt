package com.kairo.reader.data.books

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

internal data class ZipReadPolicy(
    val maxEntries: Int,
    val maxEntryBytes: Long,
    val maxTotalUncompressedBytes: Long,
    val includeEntry: (normalizedName: String) -> Boolean,
)

internal data class ImportedZipEntry(val name: String, val bytes: ByteArray,)

internal object BoundedZipReader {
    fun read(
        archiveBytes: ByteArray,
        policy: ZipReadPolicy,
    ): List<ImportedZipEntry> {
        require(archiveBytes.hasZipSignature()) { "File is not a valid ZIP archive" }
        try {
            ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
                return readArchive(zip, policy)
            }
        } catch (error: ZipException) {
            throw IllegalArgumentException("File is not a valid ZIP archive", error)
        }
    }

    private fun readArchive(
        zip: ZipInputStream,
        policy: ZipReadPolicy,
    ): List<ImportedZipEntry> {
        val entries = mutableListOf<ImportedZipEntry>()
        val state = ZipReadState()
        while (true) {
            val entry = zip.nextEntry ?: break
            state.entryCount += 1
            require(state.entryCount <= policy.maxEntries) { "ZIP archive contains too many entries" }
            val normalizedName = entry.safeNormalizedName()
            // Directory entries can contain compressed payloads too. Drain every entry through the limits.
            readEntry(zip, normalizedName, policy, state)?.let(entries::add)
            zip.closeEntry()
        }
        return entries
    }

    private fun readEntry(
        zip: ZipInputStream,
        normalizedName: String,
        policy: ZipReadPolicy,
        state: ZipReadState,
    ): ImportedZipEntry? {
        val output = if (!normalizedName.endsWith('/') && policy.includeEntry(normalizedName)) ByteArrayOutputStream() else null
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val count = zip.read(buffer)
            if (count == -1) break
            if (count == 0) continue
            entryBytes += count
            state.totalUncompressedBytes += count
            require(entryBytes <= policy.maxEntryBytes) { "ZIP entry is too large: $normalizedName" }
            require(state.totalUncompressedBytes <= policy.maxTotalUncompressedBytes) {
                "ZIP archive expands beyond the import limit"
            }
            output?.write(buffer, 0, count)
        }
        return output?.let { bytes -> ImportedZipEntry(normalizedName, bytes.toByteArray()) }
    }

    private fun ZipEntry.safeNormalizedName(): String {
        val normalized = name.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank()) { "ZIP archive contains an unnamed entry" }
        require(normalized.split('/').none { segment -> segment == ".." }) {
            "ZIP archive contains an unsafe path"
        }
        return normalized.lowercase(Locale.ROOT)
    }

    private fun ByteArray.hasZipSignature(): Boolean =
        size >= ZIP_SIGNATURE.size && ZIP_SIGNATURE.indices.all { index -> this[index] == ZIP_SIGNATURE[index] }

    // ZIP local-header bytes are a fixed protocol signature.
    @Suppress("MagicNumber")
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    private data class ZipReadState(var entryCount: Int = 0, var totalUncompressedBytes: Long = 0L,)
}
