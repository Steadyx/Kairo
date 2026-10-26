package com.kairo.reader.ui.reader

internal data class ReaderSearchPageTarget(val page: Int, val index: Int)

internal data class PendingReaderSearchPage(val query: String, val target: ReaderSearchPageTarget)

internal fun readerSearchPageTarget(
    index: Int,
    count: Int,
    page: Int,
    hasMore: Boolean,
    forward: Boolean,
): ReaderSearchPageTarget? =
    if (forward) {
        when {
            index + 1 < count -> ReaderSearchPageTarget(page, index + 1)
            hasMore -> ReaderSearchPageTarget(page + 1, 0)
            else -> null
        }
    } else {
        when {
            index > 0 -> ReaderSearchPageTarget(page, index - 1)
            page > 0 -> ReaderSearchPageTarget(page - 1, -1)
            else -> null
        }
    }
