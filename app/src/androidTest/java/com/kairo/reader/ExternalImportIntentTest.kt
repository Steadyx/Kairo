package com.kairo.reader

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalImportIntentTest {
    @Test
    fun sharedTextFileUsesStreamWhilePlainTextDoesNot() {
        val uri = Uri.parse("content://example.test/book.txt")
        val file = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hello")
        assertEquals(uri, file.bookImportUri())
        assertNull(text.bookImportUri())
        assertEquals(uri, Intent(Intent.ACTION_VIEW, uri).bookImportUri())
        assertNull(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).bookImportUri())
    }
}
