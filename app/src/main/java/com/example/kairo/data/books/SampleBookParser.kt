package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import com.example.kairo.core.model.Book
import com.example.kairo.sample.SampleBooks

@Suppress("unused")
class SampleBookParser : BookParser {
    override suspend fun parse(context: Context, uri: Uri): Book {
        return SampleBooks.defaultSample()
    }

    override fun supports(extension: String): Boolean = true
}
