package com.kairo.reader.data.books

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kairo.reader.core.dispatchers.DefaultDispatcherProvider
import com.kairo.reader.core.model.BookId
import com.kairo.reader.data.books.mobi.MobiTextLimitException
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobiImportLimitsDeviceTest {
    @Test
    fun expansionLimitDoesNotFallBackToImportingAPartialBook() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "mobi-limit-${UUID.randomUUID()}.mobi")
        val introduction = "<p>A readable introduction with more than five words.</p>".toByteArray()
        val bomb = byteArrayOf('A'.code.toByte()) + ByteArray(220_000) { if (it % 2 == 0) 0x80.toByte() else 0x0F }
        val firstRecord = 78 + 3 * 8
        val firstText = firstRecord + 16
        val secondText = firstText + introduction.size
        val data = ByteBuffer.allocate(secondText + bomb.size)
        "BOOKMOBI".toByteArray().copyInto(data.array(), 60)
        data.putShort(76, 3)
        data.putInt(78, firstRecord)
        data.putInt(86, firstText)
        data.putInt(94, secondText)
        data.putShort(firstRecord, 2)
        data.putShort(firstRecord + 8, 2)
        introduction.copyInto(data.array(), firstText)
        bomb.copyInto(data.array(), secondText)
        try {
            source.writeBytes(data.array())
            assertThrows(MobiTextLimitException::class.java) {
                runBlocking {
                    MobiBookParser(DefaultDispatcherProvider()).parse(context, Uri.fromFile(source), BookId("limit-fixture"))
                }
            }
        } finally {
            source.delete()
        }
    }
}
