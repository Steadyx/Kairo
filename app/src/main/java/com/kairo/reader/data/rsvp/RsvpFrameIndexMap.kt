package com.kairo.reader.data.rsvp

import com.kairo.reader.core.model.RsvpFrame
import com.kairo.reader.core.model.RsvpResumeCursor
import com.kairo.reader.core.model.TokenType

class RsvpFrameIndexMap private constructor(
    private val wordFrameByResumeCursor: Map<Pair<Int, Int>, Int>,
    private val frameByResumeCursor: Map<Pair<Int, Int>, Int>,
    private val wordFramesByCharacterOffset: Map<Int, SortedFrameIndex>,
    private val wordFrameByTokenIndex: Map<Int, Int>,
    private val frameByTokenIndex: Map<Int, Int>,
    private val priorWordFramesByTokenIndex: SortedFrameIndex,
    private val priorFramesByTokenIndex: SortedFrameIndex,
) {
    fun alignFrameIndex(
        tokenIndex: Int,
        resumeCursor: Int = -1,
        frameCount: Int,
    ): Int {
        if (frameCount <= 0) return 0
        val resumeFrame =
            resumeCursor
                .takeIf { it >= 0 }
                ?.let { cursor ->
                    wordFrameByResumeCursor[tokenIndex to cursor]
                        ?: RsvpResumeCursor.characterOffset(cursor)?.let { offset ->
                            wordFramesByCharacterOffset[tokenIndex]?.frameIndexBefore(offset + 1)
                        }
                        ?: frameByResumeCursor[tokenIndex to cursor]
                }
        val aligned =
            resumeFrame
                ?: wordFrameByTokenIndex[tokenIndex]
                ?: frameByTokenIndex[tokenIndex]
                ?: priorWordFramesByTokenIndex.frameIndexBefore(tokenIndex)
                ?: priorFramesByTokenIndex.frameIndexBefore(tokenIndex)
                ?: 0
        return aligned.coerceFrameIndex(frameCount)
    }

    companion object {
        val EMPTY: RsvpFrameIndexMap =
            RsvpFrameIndexMap(
                wordFrameByResumeCursor = emptyMap(),
                frameByResumeCursor = emptyMap(),
                wordFramesByCharacterOffset = emptyMap(),
                wordFrameByTokenIndex = emptyMap(),
                frameByTokenIndex = emptyMap(),
                priorWordFramesByTokenIndex = SortedFrameIndex.EMPTY,
                priorFramesByTokenIndex = SortedFrameIndex.EMPTY,
            )

        fun from(frames: List<RsvpFrame>): RsvpFrameIndexMap {
            if (frames.isEmpty()) return EMPTY

            val wordFrameByResumeCursor = mutableMapOf<Pair<Int, Int>, Int>()
            val frameByResumeCursor = mutableMapOf<Pair<Int, Int>, Int>()
            val wordFramesByCharacterOffset = mutableMapOf<Int, MutableMap<Int, Int>>()
            val wordFrameByTokenIndex = mutableMapOf<Int, Int>()
            val frameByTokenIndex = mutableMapOf<Int, Int>()

            frames.forEachIndexed { index, frame ->
                val tokenIndex = frame.originalTokenIndex
                val resumeCursor = frame.resumeCursor
                val hasWord = frame.tokens.any { it.type == TokenType.WORD }

                frameByTokenIndex.putIfAbsent(tokenIndex, index)
                if (hasWord) {
                    wordFrameByTokenIndex.putIfAbsent(tokenIndex, index)
                }
                if (resumeCursor >= 0) {
                    frameByResumeCursor.putIfAbsent(tokenIndex to resumeCursor, index)
                    if (hasWord) {
                        wordFrameByResumeCursor.putIfAbsent(tokenIndex to resumeCursor, index)
                        RsvpResumeCursor.characterOffset(resumeCursor)?.takeIf { it > 0 }?.let { offset ->
                            wordFramesByCharacterOffset.getOrPut(tokenIndex) {
                                mutableMapOf(0 to wordFrameByTokenIndex.getValue(tokenIndex))
                            }
                                .putIfAbsent(offset, index)
                        }
                    }
                }
            }

            return RsvpFrameIndexMap(
                wordFrameByResumeCursor = wordFrameByResumeCursor,
                frameByResumeCursor = frameByResumeCursor,
                wordFramesByCharacterOffset = wordFramesByCharacterOffset.mapValues { (_, offsets) ->
                    SortedFrameIndex.from(offsets)
                },
                wordFrameByTokenIndex = wordFrameByTokenIndex,
                frameByTokenIndex = frameByTokenIndex,
                priorWordFramesByTokenIndex = SortedFrameIndex.from(wordFrameByTokenIndex),
                priorFramesByTokenIndex = SortedFrameIndex.from(frameByTokenIndex),
            )
        }
    }

    private fun Int.coerceFrameIndex(frameCount: Int): Int = coerceIn(0, frameCount - 1)
}

private class SortedFrameIndex private constructor(private val tokenIndices: IntArray, private val frameIndices: IntArray,) {
    fun frameIndexBefore(tokenIndex: Int): Int? {
        var low = 0
        var high = tokenIndices.lastIndex
        var resultIndex = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (tokenIndices[mid] < tokenIndex) {
                resultIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return frameIndices.getOrNull(resultIndex)
    }

    companion object {
        val EMPTY = SortedFrameIndex(IntArray(0), IntArray(0))

        fun from(frameByTokenIndex: Map<Int, Int>): SortedFrameIndex {
            if (frameByTokenIndex.isEmpty()) return EMPTY
            val tokenIndices = frameByTokenIndex.keys.sorted().toIntArray()
            val frameIndices = IntArray(tokenIndices.size) { index ->
                frameByTokenIndex.getValue(tokenIndices[index])
            }
            return SortedFrameIndex(tokenIndices, frameIndices)
        }
    }
}
