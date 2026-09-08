package com.kairo.reader.data.books

import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** Counts all expanded bytes, including ignored assets and entries skipped by nextEntry. */
internal class EpubZipInputStream(
    input: InputStream,
    private val checkActive: () -> Unit,
    private val maxEntryBytes: Long = MAX_ENTRY_BYTES,
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    private val maxEntries: Int = MAX_ENTRIES,
) : ZipInputStream(input) {
    private var entryBytes = 0L
    private var totalBytes = 0L
    private var entries = 0
    private val discardBuffer = ByteArray(DEFAULT_BUFFER_SIZE)

    override fun getNextEntry(): ZipEntry? {
        checkActive()
        val entry = super.getNextEntry() ?: return null
        entryBytes = 0L
        entries += 1
        require(entries <= maxEntries) { "EPUB contains too many ZIP entries" }
        return entry
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkActive()
        val count = super.read(buffer, offset, length)
        if (count > 0) {
            entryBytes += count
            totalBytes += count
            require(entryBytes <= maxEntryBytes) { "EPUB entry exceeds the decompression limit" }
            require(totalBytes <= maxTotalBytes) { "EPUB exceeds the total decompression limit" }
        }
        return count
    }

    override fun closeEntry() {
        // Never let skipping an unneeded entry bypass accounting or cancellation.
        while (read(discardBuffer) != -1) {
            // The bounded read performs all checks before discarding these bytes.
        }
        super.closeEntry()
    }

    private companion object {
        const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 256L * 1024L * 1024L
        const val MAX_ENTRIES = 10_000
    }
}
