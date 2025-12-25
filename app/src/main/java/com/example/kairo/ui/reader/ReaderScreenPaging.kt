package com.example.kairo.ui.reader

internal fun sliceBlocksForPage(
    blocks: List<ReaderBlock>,
    pageStart: Int,
    pageEnd: Int,
): List<ReaderBlock> {
    if (blocks.isEmpty()) return emptyList()
    val sliced = mutableListOf<ReaderBlock>()
    var lastParagraphIncluded = false

    for (block in blocks) {
        when (block) {
            is ReaderParagraphBlock -> {
                val paragraph = block.paragraph
                val blockStart = paragraph.startIndex
                val blockEnd = paragraph.startIndex + paragraph.tokens.size - 1

                if (blockEnd < pageStart) {
                    lastParagraphIncluded = false
                    continue
                }
                if (blockStart > pageEnd) break

                val localStart = (pageStart - blockStart).coerceAtLeast(0)
                val localEnd =
                    (pageEnd - blockStart).coerceAtMost(paragraph.tokens.lastIndex)
                if (localStart > localEnd) {
                    lastParagraphIncluded = false
                    continue
                }

                val slicedParagraph =
                    if (localStart == 0 && localEnd == paragraph.tokens.lastIndex) {
                        paragraph
                    } else {
                        Paragraph(
                            tokens = paragraph.tokens.subList(localStart, localEnd + 1),
                            startIndex = blockStart + localStart,
                        )
                    }

                sliced.add(ReaderParagraphBlock(slicedParagraph))
                lastParagraphIncluded = true
            }
            is ReaderImageBlock -> {
                if (lastParagraphIncluded) {
                    sliced.add(block)
                }
            }
        }
    }

    return sliced
}
