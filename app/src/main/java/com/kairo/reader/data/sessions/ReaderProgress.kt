package com.kairo.reader.data.sessions

/**
 * A compact reader position whose absolute word coordinate was resolved against one immutable
 * snapshot of the book's chapter word counts.
 */
data class ReaderProgress(val location: ReadingSessionLocation, val absoluteWordIndex: Int?, val basisFingerprint: Long,)

/** Computes chapter prefixes once when the count snapshot changes, never during focus movement. */
class ReaderWordBasis private constructor(
    private val chapterWordCounts: IntArray,
    private val chapterWordOffsets: IntArray,
    private val fingerprint: Long,
) {
    fun progress(location: ReadingSessionLocation): ReaderProgress {
        val absoluteWordIndex =
            if (location.wordIndex < 0 || location.chapterIndex !in chapterWordCounts.indices) {
                null
            } else {
                val chapterWords = chapterWordCounts[location.chapterIndex]
                safeWordSum(
                    chapterWordOffsets[location.chapterIndex],
                    location.wordIndex.coerceIn(0, chapterWords),
                )
            }
        return ReaderProgress(location, absoluteWordIndex, fingerprint)
    }

    companion object {
        fun from(bookWordCounts: List<Int>): ReaderWordBasis {
            val counts = IntArray(bookWordCounts.size)
            val offsets = IntArray(bookWordCounts.size)
            var prefix = 0
            var hash = FNV_OFFSET_BASIS
            bookWordCounts.forEachIndexed { index, count ->
                val safeCount = count.coerceAtLeast(0)
                counts[index] = safeCount
                offsets[index] = prefix
                prefix = safeWordSum(prefix, safeCount)
                hash = (hash xor safeCount.toLong()) * FNV_PRIME
                hash = (hash xor index.toLong()) * FNV_PRIME
            }
            hash = (hash xor bookWordCounts.size.toLong()) * FNV_PRIME
            return ReaderWordBasis(counts, offsets, hash)
        }

        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
    }
}

private fun safeWordSum(
    first: Int,
    second: Int,
): Int = (first.toLong() + second.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
