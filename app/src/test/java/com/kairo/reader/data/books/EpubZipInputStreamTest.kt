package com.kairo.reader.data.books

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubZipInputStreamTest {
    @Test
    fun skippingCompressedAssetStillEnforcesEntryBudget() {
        val archive = archive("ignored.bin" to ByteArray(64 * 1024), "chapter.xhtml" to byteArrayOf(1))
        assertTrue(archive.size < 1024)
        EpubZipInputStream(ByteArrayInputStream(archive), {}, maxEntryBytes = 1024).use { zip ->
            assertEquals("ignored.bin", requireNotNull(zip.nextEntry).name)
            val error = assertThrows(IllegalArgumentException::class.java) { zip.nextEntry }
            assertTrue(error.message.orEmpty().contains("decompression limit"))
        }
    }

    @Test
    fun totalBudgetIncludesIgnoredEntriesAndDirectories() {
        val archive = archive("ignored.bin" to ByteArray(8), "directory/" to ByteArray(8))
        EpubZipInputStream(ByteArrayInputStream(archive), {}, maxTotalBytes = 12).use { zip ->
            zip.nextEntry
            zip.closeEntry()
            zip.nextEntry
            assertThrows(IllegalArgumentException::class.java) { zip.closeEntry() }
        }
    }

    @Test
    fun cancellationInterruptsDiscardingAnEntry() {
        var cancelled = false
        val archive = archive("ignored.bin" to ByteArray(64 * 1024))
        EpubZipInputStream(ByteArrayInputStream(archive), {
            if (cancelled) throw CancellationException("cancelled")
        }).use { zip ->
            zip.nextEntry
            zip.read()
            cancelled = true
            assertThrows(CancellationException::class.java) { zip.closeEntry() }
        }
    }

    @Test
    fun entryCountIncludesEmptyDirectories() {
        val archive = archive("one/" to byteArrayOf(), "two/" to byteArrayOf())
        EpubZipInputStream(ByteArrayInputStream(archive), {}, maxEntries = 1).use { zip ->
            zip.nextEntry
            assertThrows(IllegalArgumentException::class.java) { zip.nextEntry }
        }
    }

    @Test
    fun validEntriesCanBeReadAndSkippedWithinTheBudget() {
        val archive = archive("ignored.bin" to ByteArray(8), "chapter.xhtml" to "hello".toByteArray())
        EpubZipInputStream(ByteArrayInputStream(archive), {}, maxTotalBytes = 13).use { zip ->
            zip.nextEntry
            assertEquals("chapter.xhtml", requireNotNull(zip.nextEntry).name)
            assertEquals("hello", zip.readBytes().toString(Charsets.UTF_8))
            assertEquals(null, zip.nextEntry)
        }
    }

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
