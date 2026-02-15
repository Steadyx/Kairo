package com.example.kairo.data.books.mobi

import java.nio.charset.Charset

internal object MobiLimits {
    const val MAX_FILE_SIZE_BYTES: Long = 50L * 1024L * 1024L
    const val MIN_FILE_SIZE_BYTES = 78
    const val MIN_RECORD_COUNT = 2
    const val MAX_IMAGE_ENTRY_SIZE = 2 * 1024 * 1024
    const val MAX_COVER_IMAGE_ENTRY_SIZE = 6 * 1024 * 1024
    const val MAX_TOTAL_IMAGE_SIZE = 25 * 1024 * 1024
    const val MAX_FALLBACK_TEXT_CHARS = 250_000
    const val MAX_CHAPTER_WORDS = 3500
    const val MAX_CHAPTER_TEXT_CHARS = MAX_CHAPTER_WORDS * 7
    const val MAX_FILEPOS_CHAPTERS = 400
    const val MIN_FILEPOS_GAP_CHARS = 1200
    const val TOC_ANCHOR_THRESHOLD = 12
    const val MAX_TOC_TEXT_CHARS = 200_000
    const val COVER_FALLBACK_IMAGE_SCAN = 12
    const val COVER_HTML_SCAN_CHARS = 120_000
    const val MIN_COLOR_COVER_AREA = 120_000
    const val MIN_COLOR_SCORE = 0.08f
    const val MOBI_HEADER_OFFSET = 16
    const val FIRST_IMAGE_INDEX_OFFSET = 0x6C
    const val EXTH_FLAGS_OFFSET = 0x80
    const val EXTH_PRESENT_FLAG = 0x40
}

internal data class PalmDocHeader(
    val compression: Int,
    val textRecordCount: Int,
)

internal data class MobiHeader(
    val title: String,
    val authors: List<String>,
    val textCharset: Charset,
    val firstImageIndex: Int,
    val coverRecordIndex: Int?,
)

internal data class MobiHeaders(
    val primary: MobiHeader,
    val kf8: MobiHeader?,
)

internal data class MobiImageType(
    val extension: String,
)

internal data class MobiImageDimensions(
    val width: Int,
    val height: Int,
) {
    val area: Long = width.toLong() * height.toLong()
    val isPortrait: Boolean = height >= width
}

internal data class MobiImageExtraction(
    val imagePathByRecordIndex: Map<Int, String>,
    val coverImage: ByteArray?,
    val resolvedFirstImageIndex: Int?,
    val recindexBase: Int?,
)

internal data class MobiChapterSlice(
    val start: Int,
    val end: Int,
    val title: String?,
    val htmlContent: String,
)

internal data class TocEntry(
    val filepos: Int,
    val title: String,
)
