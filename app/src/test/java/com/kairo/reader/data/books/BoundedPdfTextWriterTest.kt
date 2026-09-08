package com.kairo.reader.data.books

import java.io.StringWriter
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedPdfTextWriterTest {
    @Test
    fun rejectsAdditionalTextBeforeAppendingIt() {
        val output = StringWriter()
        val writer = BoundedPdfTextWriter(output, maxChars = 5, checkActive = {})
        writer.write("Hello")
        assertThrows(IllegalArgumentException::class.java) { writer.write(" world") }
        assertEquals("Hello", output.toString())
    }

    @Test
    fun cancellationStopsOutputWithoutAppendingMoreText() {
        val output = StringWriter()
        var cancelled = false
        val writer = BoundedPdfTextWriter(output, maxChars = 100) {
            if (cancelled) throw CancellationException("cancelled")
        }
        writer.write("Hello")
        cancelled = true
        assertThrows(CancellationException::class.java) { writer.write(" world") }
        assertEquals("Hello", output.toString())
    }
}
