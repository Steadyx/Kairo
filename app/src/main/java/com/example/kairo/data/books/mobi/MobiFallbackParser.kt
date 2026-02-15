package com.example.kairo.data.books.mobi

import com.example.kairo.core.model.Book
import com.example.kairo.core.model.BookId

internal class MobiFallbackParser(
    private val contentProcessor: MobiContentProcessor = MobiContentProcessor(),
) {
    fun parse(
        bookId: BookId,
        data: ByteArray,
        fileName: String,
    ): Book {
        val extracted = contentProcessor.extractFallbackText(data)
        val text =
            when {
                extracted.isBlank() -> "No readable content found."
                else -> extracted
            }
        val chapters = contentProcessor.splitFallbackText(text)
        return Book(
            id = bookId,
            title = fileName.substringBeforeLast('.', "MOBI Import"),
            authors = emptyList(),
            languageTag = null,
            coverImage = null,
            chapters = chapters,
        )
    }
}
