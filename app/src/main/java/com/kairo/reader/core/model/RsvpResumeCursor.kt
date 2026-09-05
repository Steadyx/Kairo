package com.kairo.reader.core.model

/**
 * Stores a source-character offset in the existing saved/navigation cursor field.
 * The tag distinguishes these positions from older, generation-relative expanded cursors.
 * Always pair a cursor with its original token index; it is not a chapter-wide frame ID.
 */
object RsvpResumeCursor {
    fun fromCharacterOffset(offset: Int): Int = SOURCE_OFFSET_TAG + offset.coerceIn(0, MAX_OFFSET)

    fun characterOffset(cursor: Int): Int? =
        cursor.takeIf { it >= SOURCE_OFFSET_TAG }?.minus(SOURCE_OFFSET_TAG)

    private const val SOURCE_OFFSET_TAG = 1 shl 30
    private const val MAX_OFFSET = SOURCE_OFFSET_TAG - 1
}
