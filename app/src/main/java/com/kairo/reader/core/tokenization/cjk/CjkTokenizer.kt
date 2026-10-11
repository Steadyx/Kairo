package com.kairo.reader.core.tokenization.cjk

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.withoutInlinePhysicalPageBreaks
import com.kairo.reader.core.tokenization.ChapterTokenizer
import com.kairo.reader.core.tokenization.HtmlEmphasisApplier

class CjkTokenizer(config: CjkSegmenterConfig = CjkSegmenterConfig(),) : ChapterTokenizer {
    private val segmenter = CjkSegmenter(config)

    override fun tokenize(chapter: Chapter): List<Token> {
        val cleanedText =
            if (CjkTextNormalizer.shouldStripPageNumbers(chapter.htmlContent)) {
                CjkTextNormalizer.stripStandalonePageNumbers(
                    text = chapter.plainText,
                    html = chapter.htmlContent,
                )
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
                tokens += CjkTokenFactory.pageBreak(CjkParagraphSplitter.pageBreakText(paragraph))
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

        val linked = CjkLinkApplier.apply(
            tokens.withoutInlinePhysicalPageBreaks().toMutableList(),
            chapter,
            segmenter::tokenizeInlineText,
        )
        return HtmlEmphasisApplier.apply(linked, chapter.htmlContent, CjkTextNormalizer::normalizeInlineText, segmenter::tokenizeInlineText)
    }
}
