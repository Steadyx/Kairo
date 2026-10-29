package com.kairo.reader.data.search

object LibrarySearchConstraints {
    const val MIN_QUERY_LENGTH = 2
    const val MAX_QUERY_LENGTH = 200
    const val MAX_RESULTS = 100
    const val PASSAGE_RESULTS_PER_PAGE = 60

    internal const val MAX_RAW_QUERY_LENGTH = MAX_QUERY_LENGTH * 2
}
