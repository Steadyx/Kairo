package com.example.kairo.data.books

import android.content.Context
import android.net.Uri
import com.example.kairo.core.model.Book

interface BookParser {
    suspend fun parse(context: Context, uri: Uri): Book
    fun supports(extension: String): Boolean
}
