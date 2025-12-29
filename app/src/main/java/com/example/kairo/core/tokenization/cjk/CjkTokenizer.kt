package com.example.kairo.core.tokenization.cjk

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.tokenization.ChapterTokenizer

class CjkTokenizer(
    config: CjkSegmenterConfig = CjkSegmenterConfig(),
) : ChapterTokenizer {
    private val segmenterConfig = config
    private val segmenter = CjkSegmenter(config)

    override fun tokenize(chapter: Chapter): List<Token> {
        val cleanedText =
            if (CjkTextNormalizer.shouldStripPageNumbers(chapter.htmlContent)) {
                CjkTextNormalizer.stripStandalonePageNumbers(chapter.plainText)
            } else {
                chapter.plainText
            }
        val normalized = CjkTextNormalizer.normalize(cleanedText)
        if (normalized.isEmpty()) return emptyList()

        val withPageBreaks = CjkTextNormalizer.normalizePageBreakMarkers(normalized)
        val paragraphs = CjkParagraphSplitter.split(withPageBreaks)
        val tokens = mutableListOf<Token>()

        paragraphs.forEachIndexed { index, paragraph ->
            val isPageBreak = CjkParagraphSplitter.isPageBreakParagraph(paragraph)
            if (isPageBreak) {
                tokens += CjkTokenFactory.pageBreak()
            } else {
                tokens += segmenter.tokenizeParagraph(paragraph)
            }

            val nextParagraph = paragraphs.getOrNull(index + 1)
            val nextIsPageBreak =
                nextParagraph?.let { CjkParagraphSplitter.isPageBreakParagraph(it) } == true
            if (index < paragraphs.lastIndex && !isPageBreak && !nextIsPageBreak) {
                tokens += CjkTokenFactory.paragraphBreak()
            }
        }

        return CjkLinkApplier.apply(
            tokens,
            chapter,
            segmenter::tokenizeInlineText,
            insertWordSpaces = segmenterConfig.treatHangulAsWord,
        )
    }
}
