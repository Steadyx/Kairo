package com.example.kairo.sample

import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId
import com.example.kairo.core.model.Chapter

object SampleBooks {
    fun defaultSample(): Book {
        val chapterOneText = """
            Kairo is an RSVP-first reader. Tap on the highlighted word to jump into speed reading mode. 
            This sample chapter is short on purpose so that you can quickly reach the RSVP view.

            Reading this way keeps your eyes still while words flash in a single spot. 
            Adjust the speed later in Settings. Remember to take breaks.
        """.trimIndent()

        val chapterTwoText = """
            Chapter two is here to prove navigation works. Scroll, tap, and swap chapters. 
            A paragraph break adds a little breathing room between ideas.

            Keep experimenting. The engine will pause after punctuation and slow slightly on longer words.
        """.trimIndent()

        return Book(
            id = BookId("sample-journey"),
            title = "The RSVP Journey",
            authors = listOf("Kairo Team"),
            coverImage = null,
            chapters = listOf(
                Chapter(
                    index = 0,
                    title = "Getting Started",
                    htmlContent = "<p>Kairo is an RSVP-first reader...</p>",
                    plainText = chapterOneText
                ),
                Chapter(
                    index = 1,
                    title = "More Motion",
                    htmlContent = "<p>Chapter two is here...</p>",
                    plainText = chapterTwoText
                )
            )
        )
    }
}
