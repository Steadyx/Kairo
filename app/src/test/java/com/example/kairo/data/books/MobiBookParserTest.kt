package com.example.kairo.data.books

import com.example.kairo.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiBookParserTest {
    private val parser = MobiBookParser(TestDispatcherProvider)

    @Test
    fun extractPlainTextKeepsInlineMbpPageBreakContent() {
        val html = "<p>Indiana and<mbp:pagebreak/> Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsClassPageBreakContent() {
        val html = "<p>Indiana and<span class=\"pagebreak\"/> Leo took up the rear.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Indiana and Leo took up the rear."))
    }

    @Test
    fun extractPlainTextKeepsContentAfterClassPageBreak() {
        val html = "<p>Start<span class=\"page-break\"/> end.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Start end."))
    }

    @Test
    fun extractPlainTextDecodesEntitiesAndPreservesParagraphs() {
        val html = "<p>Hello&nbsp;world &amp; friends.</p><p>Next&nbsp;para.</p>"

        val text: String = parser.callPrivate("extractPlainText", html)

        assertTrue(text.contains("Hello world & friends."))
        assertTrue(text.contains("Next para."))
        assertTrue(text.contains("friends.\n\nNext"))
    }
}

private object TestDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.callPrivate(name: String, vararg args: Any): T {
    val method =
        javaClass.getDeclaredMethod(
            name,
            *args.map { it.javaClass }.toTypedArray(),
        )
    method.isAccessible = true
    return method.invoke(this, *args) as T
}
