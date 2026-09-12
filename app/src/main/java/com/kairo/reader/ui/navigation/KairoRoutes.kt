package com.kairo.reader.ui.navigation

import com.kairo.reader.core.model.TimedReadingMode

internal object KairoRoutes {
    const val LIBRARY = "library"
    const val LIBRARY_WITH_TAB = "library?tab={tab}"
    const val SETTINGS = "settings"
    const val SETTINGS_LANGUAGE = "settings/language"
    const val SETTINGS_INFO = "settings/info"
    const val SETTINGS_RSVP = "settings/rsvp"
    const val SETTINGS_BIONIC = "settings/bionic"
    const val SETTINGS_THEME = "settings/theme"
    const val SETTINGS_READER = "settings/reader"
    const val SETTINGS_FOCUS = "settings/focus"
    const val READER = "reader/{bookId}"
    const val READER_WITH_POSITION =
        "reader/{bookId}/{chapterIndex}/{tokenIndex}?" +
            "searchCodePointOffset={searchCodePointOffset}"
    const val RSVP = "rsvp/{bookId}/{chapterIndex}/{tokenIndex}?tempoMs={tempoMs}"
    const val BIONIC = "bionic/{bookId}/{chapterIndex}/{tokenIndex}?tempoMs={tempoMs}"

    const val ARG_BOOK_ID = "bookId"
    const val ARG_CHAPTER_INDEX = "chapterIndex"
    const val ARG_TOKEN_INDEX = "tokenIndex"
    const val ARG_SEARCH_CODE_POINT_OFFSET = "searchCodePointOffset"
    const val ARG_TEMPO_MS = "tempoMs"
    const val ARG_LIBRARY_TAB = "tab"

    const val TAB_LIBRARY = "library"
    const val TAB_BOOKMARKS = "bookmarks"
    const val TAB_COMPLETED = "completed"

    fun libraryBookmarks(): String = "library?tab=$TAB_BOOKMARKS"

    fun reader(bookId: String): String = "reader/$bookId"

    fun reader(
        bookId: String,
        chapterIndex: Int,
        tokenIndex: Int,
        searchCodePointOffset: Int? = null,
    ): String =
        buildString {
            append("reader/$bookId/$chapterIndex/$tokenIndex")
            searchCodePointOffset
                ?.takeIf { it >= 0 }
                ?.let { append("?searchCodePointOffset=$it") }
        }

    fun rsvp(
        bookId: String,
        chapterIndex: Int,
        tokenIndex: Int,
        tempoMsPerWord: Long? = null,
    ): String {
        val encodedTempoMs = tempoMsPerWord?.takeIf { it > 0L } ?: -1L
        return "rsvp/$bookId/$chapterIndex/$tokenIndex?tempoMs=$encodedTempoMs"
    }

    fun bionic(
        bookId: String,
        chapterIndex: Int,
        tokenIndex: Int,
        tempoMsPerWord: Long? = null,
    ): String {
        val encodedTempoMs = tempoMsPerWord?.takeIf { it > 0L } ?: -1L
        return "bionic/$bookId/$chapterIndex/$tokenIndex?tempoMs=$encodedTempoMs"
    }

    fun timedReading(
        mode: TimedReadingMode,
        bookId: String,
        chapterIndex: Int,
        tokenIndex: Int,
        tempoMsPerWord: Long? = null,
    ): String =
        when (mode) {
            TimedReadingMode.RSVP ->
                rsvp(bookId, chapterIndex, tokenIndex, tempoMsPerWord)
            TimedReadingMode.BIONIC ->
                bionic(bookId, chapterIndex, tokenIndex, tempoMsPerWord)
        }
}

internal object KairoSavedStateKeys {
    const val RSVP_RESULT_CHAPTER_INDEX = "rsvp_result_chapter_index"
    const val RSVP_RESULT_TOKEN_INDEX = "rsvp_result_token_index"
    const val RSVP_RESULT_RESUME_CURSOR = "rsvp_result_resume_cursor"
    const val RSVP_RESULT_TEMPO_MS = "rsvp_result_tempo_ms"
    const val RSVP_PLAYBACK_IS_PLAYING = "rsvp_playback_is_playing"
    const val RSVP_CURRENT_TOKEN_INDEX = "rsvp_current_token_index"
    const val RSVP_CURRENT_RESUME_CURSOR = "rsvp_current_resume_cursor"
}
