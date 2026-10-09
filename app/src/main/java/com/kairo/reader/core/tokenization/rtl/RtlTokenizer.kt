package com.kairo.reader.core.tokenization.rtl

import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.withoutInlinePhysicalPageBreaks
import com.kairo.reader.core.tokenization.ChapterTokenizer
import com.kairo.reader.core.tokenization.HtmlEmphasisApplier

class RtlTokenizer(config: RtlSegmenterConfig = RtlSegmenterConfig(),) : ChapterTokenizer {
    private val segmenter = RtlSegmenter(config)

    override fun tokenize(chapter: Chapter): List<Token> {
        val cleanedText =
            if (RtlTextNormalizer.shouldStripPageNumbers(chapter.htmlContent)) {
                RtlTextNormalizer.stripStandalonePageNumbers(
                    text = chapter.plainText,
                    html = chapter.htmlContent,
                )
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
                tokens += RtlTokenFactory.pageBreak(RtlParagraphSplitter.pageBreakText(paragraph))
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

        val linked = RtlLinkApplier.apply(
            tokens.withoutInlinePhysicalPageBreaks().toMutableList(),
            chapter,
            segmenter::tokenizeInlineText,
        )
        return HtmlEmphasisApplier.apply(linked, chapter.htmlContent, RtlTextNormalizer::normalizeInlineText, segmenter::tokenizeInlineText)
    }
}
