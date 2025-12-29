package com.example.kairo.core.tokenization.rtl

import com.example.kairo.core.model.Chapter
import com.example.kairo.core.model.Token
import com.example.kairo.core.tokenization.ChapterTokenizer

class RtlTokenizer(
    config: RtlSegmenterConfig = RtlSegmenterConfig(),
) : ChapterTokenizer {
    private val segmenter = RtlSegmenter(config)

    override fun tokenize(chapter: Chapter): List<Token> {
        val cleanedText =
            if (RtlTextNormalizer.shouldStripPageNumbers(chapter.htmlContent)) {
                RtlTextNormalizer.stripStandalonePageNumbers(chapter.plainText)
            } else {
                chapter.plainText
            }
        val normalized = RtlTextNormalizer.normalize(cleanedText)
        if (normalized.isEmpty()) return emptyList()

        val withPageBreaks = RtlTextNormalizer.normalizePageBreakMarkers(normalized)
        val paragraphs = RtlParagraphSplitter.split(withPageBreaks)
        val tokens = mutableListOf<Token>()

        paragraphs.forEachIndexed { index, paragraph ->
            val isPageBreak = RtlParagraphSplitter.isPageBreakParagraph(paragraph)
            if (isPageBreak) {
                tokens += RtlTokenFactory.pageBreak()
            } else {
                tokens += segmenter.tokenizeParagraph(paragraph)
            }

            val nextParagraph = paragraphs.getOrNull(index + 1)
            val nextIsPageBreak =
                nextParagraph?.let { RtlParagraphSplitter.isPageBreakParagraph(it) } == true
            if (index < paragraphs.lastIndex && !isPageBreak && !nextIsPageBreak) {
                tokens += RtlTokenFactory.paragraphBreak()
            }
        }

        return RtlLinkApplier.apply(tokens, chapter, segmenter::tokenizeInlineText)
    }
}
