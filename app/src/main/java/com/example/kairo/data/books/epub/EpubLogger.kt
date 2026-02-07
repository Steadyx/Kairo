package com.example.kairo.data.books.epub

import android.util.Log

internal object EpubLogger {
    fun info(
        tag: String,
        message: String,
    ) {
        runCatching { Log.i(tag, message) }
    }

    fun warn(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        runCatching {
            if (error != null) {
                Log.w(tag, message, error)
            } else {
                Log.w(tag, message)
            }
        }
    }
}
