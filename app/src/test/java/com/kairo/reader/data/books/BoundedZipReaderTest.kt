package com.kairo.reader.data.books

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedZipReaderTest {
    @Test
    fun directoryPayloadCannotBypassEntryLimit() {
        val archive = zip("directory/" to ByteArray(1024 * 1024))
        assertThrows(IllegalArgumentException::class.java) {
            BoundedZipReader.read(archive, policy(entry = 1024, total = 2 * 1024 * 1024))
        }
    }

    @Test
    fun directoryPayloadCountsTowardTotalAlongsideSelectedAndIgnoredFiles() {
        val archive = zip(
            "directory/" to ByteArray(500),
            "ignored.bin" to ByteArray(500),
            "book.fb2" to ByteArray(100),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BoundedZipReader.read(archive, policy(entry = 1024, total = 1024))
        }
    }

    @Test
    fun validDirectoriesAreDrainedButNeverReturnedAsBookContent() {
        val archive = zip("empty/" to byteArrayOf(), "directory/" to ByteArray(50), "book.fb2" to "Book".toByteArray())
        val entries = BoundedZipReader.read(archive, policy(entry = 100, total = 100).copy(includeEntry = { true }))
        assertEquals(listOf("book.fb2"), entries.map { it.name })
        assertEquals("Book", entries.single().bytes.decodeToString())
    }

    private fun policy(entry: Long, total: Long) = ZipReadPolicy(10, entry, total) { it.endsWith(".fb2") }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
