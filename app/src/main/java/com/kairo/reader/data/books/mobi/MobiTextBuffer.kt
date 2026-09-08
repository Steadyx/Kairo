package com.kairo.reader.data.books.mobi

import java.io.ByteArrayOutputStream

internal class MobiTextLimitException : IllegalArgumentException("MOBI expanded text exceeds the supported size")

/** Unboxed storage with a hard limit checked before allocating or appending. */
internal class MobiTextBuffer(private val limit: Int) : ByteArrayOutputStream() {
    fun append(value: Byte) {
        requireCapacity(1)
        write(value.toInt())
    }

    fun append(bytes: ByteArray) {
        requireCapacity(bytes.size)
        write(bytes, 0, bytes.size)
    }

    operator fun get(index: Int): Byte = buf[index]

    private fun requireCapacity(extra: Int) {
        if (extra > limit - size()) throw MobiTextLimitException()
    }
}
