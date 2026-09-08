package com.kairo.reader.data.books.mobi

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiTextIntegrityTest {
    @Test
    fun unicodeRecordsAndCharactersSplitAcrossRecordsArePreserved() {
        val intro = "<html><body><p>English introduction.</p>".toByteArray()
        val text = "这是一个可以阅读的中文段落。日本語の文章です。".repeat(20)
        val bytes = text.toByteArray()
        val records = listOf(intro, bytes.copyOfRange(0, 1), bytes.copyOfRange(1, bytes.size), "</body></html>".toByteArray())
        val result = extract(records)
        assertTrue(result.contains("English introduction."))
        assertTrue(result.contains(text))
        assertFalse(result.contains('\uFFFD'))
        assertFalse(MobiBinary.looksMostlyBinary(bytes))
        assertTrue(MobiBinary.looksMostlyBinary(ByteArray(100)))
    }

    @Test
    fun overlappingPalmDocBackReferencesRemainCorrectAtTheLimit() {
        val compressed = byteArrayOf('A'.code.toByte(), 0x80.toByte(), 0x0F)
        assertEquals("A".repeat(11), String(MobiBinary.decompressPalmDoc(compressed, 11)))
        assertThrows(MobiTextLimitException::class.java) { MobiBinary.decompressPalmDoc(compressed, 10) }
    }

    @Test
    fun aggregateLimitAppliesAcrossIndividuallyValidCompressedRecords() {
        val compressed = byteArrayOf('A'.code.toByte()) + ByteArray(100_000) { if (it % 2 == 0) 0x80.toByte() else 0x0F }
        assertThrows(MobiTextLimitException::class.java) { extract(List(17) { compressed }, compression = 2) }
    }

    @Test
    fun uncompressedRecordsCannotBypassTheAggregateLimit() {
        val record = ByteArray(MobiLimits.MAX_TEXT_RECORD_BYTES) { 'A'.code.toByte() }
        assertThrows(MobiTextLimitException::class.java) { extract(List(9) { record }) }
    }

    @Test
    fun decompressionAndRecordExtractionObserveCancellation() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            MobiBinary.decompressPalmDoc(ByteArray(4096) { 'A'.code.toByte() }) {
                if (++checks == 2) throw CancellationException()
            }
        }
        assertEquals(2, checks)
        assertThrows(CancellationException::class.java) {
            extract(listOf("<p>Readable text</p>".toByteArray()), processor = MobiContentProcessor { throw CancellationException() })
        }
    }

    private fun extract(
        records: List<ByteArray>,
        compression: Int = 1,
        processor: MobiContentProcessor = MobiContentProcessor(),
    ): String {
        val all = listOf(ByteArray(16)) + records
        val offsets = all.runningFold(0) { total, bytes -> total + bytes.size }.dropLast(1)
        val data = ByteArray(all.sumOf { it.size })
        all.forEachIndexed { index, bytes -> bytes.copyInto(data, offsets[index]) }
        return processor.extractHtml(
            data,
            offsets,
            compression,
            records.size,
            MobiHeader("Book", emptyList(), Charsets.UTF_8, -1, null),
            -1,
        )
    }
}
