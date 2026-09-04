@file:Suppress("MagicNumber")

package com.kairo.reader.data.export

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.kairo.reader.R
import com.kairo.reader.core.export.NoteExportDocument
import com.kairo.reader.core.export.NoteExportEntry
import com.kairo.reader.core.export.NoteExportLocalization
import com.kairo.reader.core.export.NoteExportSource
import com.kairo.reader.core.model.HighlightColor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

internal class NoteExportPdfRenderer(private val context: Context, private val localization: NoteExportLocalization,) {
    fun render(
        document: NoteExportDocument,
        target: File,
        cancellationCheck: () -> Unit = {},
    ) {
        val pdf = PdfDocument()
        try {
            PdfLayoutEngine(pdf, context, localization, cancellationCheck).render(document)
            FileOutputStream(target).use(pdf::writeTo)
        } finally {
            pdf.close()
        }
    }
}

private class PdfLayoutEngine(
    private val document: PdfDocument,
    private val context: Context,
    private val localization: NoteExportLocalization,
    private val cancellationCheck: () -> Unit,
) {
    private val sansRegular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val serifRegular = Typeface.create("serif", Typeface.NORMAL)
    private val titlePaint = textPaint(27f, PAGE_TEXT, sansMedium)
    private val sourcePaint = textPaint(20f, PAGE_TEXT, sansMedium)
    private val authorPaint = textPaint(10.5f, MUTED_TEXT, sansRegular)
    private val chapterPaint = textPaint(11.5f, PRIMARY, sansMedium)
    private val notePaint = textPaint(13.5f, PAGE_TEXT, serifRegular, lineSpacingMultiplier = 1.13f)
    private val passagePaint = textPaint(11.5f, PAGE_TEXT, serifRegular, lineSpacingMultiplier = 1.12f)
    private val metadataPaint = textPaint(9.5f, MUTED_TEXT, sansRegular)
    private val contextPaint = textPaint(9.5f, MUTED_TEXT, sansMedium)
    private val contextChapterPaint = textPaint(8.5f, PRIMARY, sansRegular)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PRIMARY
        textSize = 9.5f
        typeface = sansMedium
    }
    private val mastheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PRIMARY
        textSize = 15f
        typeface = sansMedium
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FOOTER_TEXT
        textSize = 8.5f
        typeface = sansRegular
    }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RULE
        strokeWidth = 0.75f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var currentPage: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var cursorY = CONTENT_TOP
    private var pageNumber = 0

    fun render(export: NoteExportDocument) {
        startPage()
        try {
            drawFirstPageHeader(export)
            export.sources.forEachIndexed { index, source ->
                cancellationCheck()
                if (index > 0) {
                    ensureSpace(SOURCE_MINIMUM_SPACE)
                    drawRule(SOURCE_RULE_TOP_GAP, SOURCE_RULE_BOTTOM_GAP)
                }
                drawSource(source)
            }
        } finally {
            finishPage()
        }
    }

    private fun drawFirstPageHeader(export: NoteExportDocument) {
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.kairo_logo_icon)
        if (logo != null) {
            canvas.drawBitmap(
                logo,
                null,
                RectF(PAGE_MARGIN, cursorY, PAGE_MARGIN + LOGO_SIZE, cursorY + LOGO_SIZE),
                fillPaint,
            )
        }
        canvas.drawText(
            KAIRO_MASTHEAD,
            PAGE_MARGIN + LOGO_SIZE + 11f,
            cursorY + LOGO_SIZE * 0.68f,
            mastheadPaint,
        )
        cursorY += LOGO_SIZE + 18f
        drawLayoutCompletely(
            layout(localization.documentTitle(export.scope), titlePaint, CONTENT_WIDTH.toInt()),
            PAGE_MARGIN,
        )
        cursorY += 11f
        drawLayoutCompletely(
            layout(
                localization.exportedOn(localization.formatDate(export.generatedAt)),
                metadataPaint,
                CONTENT_WIDTH.toInt(),
            ),
            PAGE_MARGIN,
        )
        cursorY += 3f
        drawLayoutCompletely(
            layout(
                localization.contentsSummary(export.noteCount, export.sources.size),
                metadataPaint,
                CONTENT_WIDTH.toInt(),
            ),
            PAGE_MARGIN,
        )
        cursorY += 20f
        drawRule(0f, 20f)
    }

    private fun drawSource(source: NoteExportSource) {
        val authors =
            if (source.authors.isEmpty()) {
                localization.unknownAuthor
            } else {
                source.authors.joinToString(AUTHOR_SEPARATOR)
            }
        val sourceHeaderHeight =
            layout(source.title, sourcePaint, CONTENT_WIDTH.toInt()).height +
                5f +
                layout(
                    "${localization.authorLabel}: $authors",
                    authorPaint,
                    CONTENT_WIDTH.toInt(),
                ).height +
                14f
        ensureSpace(sourceHeaderHeight + ENTRY_MINIMUM_SPACE)
        drawFlowingText(
            text = source.title,
            paint = sourcePaint,
            left = PAGE_MARGIN,
            width = CONTENT_WIDTH,
        )
        cursorY += 5f
        drawFlowingText(
            text = "${localization.authorLabel}: $authors",
            paint = authorPaint,
            left = PAGE_MARGIN,
            width = CONTENT_WIDTH,
        )
        cursorY += 14f
        source.entries.forEachIndexed { index, entry ->
            if (index > 0) drawRule(11f, 15f)
            drawEntry(source, entry)
        }
    }

    private fun drawEntry(
        source: NoteExportSource,
        entry: NoteExportEntry,
    ) {
        cancellationCheck()
        val chapter = entry.chapterTitle ?: localization.chapterFallback(entry.chapterNumber)
        val context = ContinuationContext(source.title, chapter)
        ensureSpace(ENTRY_MINIMUM_SPACE, context)
        drawFlowingText(
            text = chapter,
            paint = chapterPaint,
            left = PAGE_MARGIN,
            width = CONTENT_WIDTH,
            continuationContext = context,
        )
        cursorY += 10f
        drawLabel(localization.noteLabel)
        cursorY += 6f
        drawFlowingText(
            text = entry.note,
            paint = notePaint,
            left = PAGE_MARGIN,
            width = CONTENT_WIDTH,
            continuationContext = context,
            continuationLabel = localization.noteLabel,
        )
        cursorY += 13f
        drawPassage(entry, context)
        cursorY += 10f
        val metadata = buildString {
            append(localization.createdLabel)
            append(": ")
            append(localization.formatDate(entry.createdAt))
            if (entry.updatedAt != entry.createdAt) {
                append("\n")
                append(localization.updatedLabel)
                append(": ")
                append(localization.formatDate(entry.updatedAt))
            }
        }
        drawFlowingText(
            text = metadata,
            paint = metadataPaint,
            left = PAGE_MARGIN,
            width = CONTENT_WIDTH,
            continuationContext = context,
        )
        cursorY += 5f
    }

    private fun drawPassage(
        entry: NoteExportEntry,
        context: ContinuationContext,
    ) {
        val textLayout =
            layout(
                entry.passage,
                passagePaint,
                (CONTENT_WIDTH - PASSAGE_HORIZONTAL_INSET * 2f).toInt(),
            )
        var startLine = 0
        var segmentIndex = 0
        while (startLine < textLayout.lineCount) {
            cancellationCheck()
            val label =
                if (segmentIndex == 0) {
                    localization.passageLabel
                } else {
                    localization.continued(localization.passageLabel)
                }
            val fixedHeight = PASSAGE_TOP_PADDING + LABEL_HEIGHT + PASSAGE_LABEL_GAP + PASSAGE_BOTTOM_PADDING
            val availableTextHeight = CONTENT_BOTTOM - cursorY - fixedHeight
            var endLine = findFittingLineEnd(textLayout, startLine, availableTextHeight)
            if (endLine == startLine) {
                startPage(context)
                continue
            }
            val textHeight = lineSegmentHeight(textLayout, startLine, endLine)
            val blockHeight = fixedHeight + textHeight
            fillPaint.color = passageBackground(entry.highlightColor)
            canvas.drawRoundRect(
                RectF(PAGE_MARGIN, cursorY, PAGE_MARGIN + CONTENT_WIDTH, cursorY + blockHeight),
                PASSAGE_CORNER_RADIUS,
                PASSAGE_CORNER_RADIUS,
                fillPaint,
            )
            fillPaint.color = highlightAccent(entry.highlightColor)
            canvas.drawRoundRect(
                RectF(PAGE_MARGIN, cursorY, PAGE_MARGIN + PASSAGE_ACCENT_WIDTH, cursorY + blockHeight),
                PASSAGE_ACCENT_RADIUS,
                PASSAGE_ACCENT_RADIUS,
                fillPaint,
            )
            canvas.drawText(
                label,
                PAGE_MARGIN + PASSAGE_HORIZONTAL_INSET,
                cursorY + PASSAGE_TOP_PADDING + LABEL_BASELINE,
                labelPaint,
            )
            val textTop = cursorY + PASSAGE_TOP_PADDING + LABEL_HEIGHT + PASSAGE_LABEL_GAP
            drawLineSegment(
                textLayout,
                startLine,
                endLine,
                PAGE_MARGIN + PASSAGE_HORIZONTAL_INSET,
                textTop,
            )
            cursorY += blockHeight
            startLine = endLine
            segmentIndex++
            if (startLine < textLayout.lineCount) startPage(context)
        }
    }

    private fun drawFlowingText(
        text: String,
        paint: TextPaint,
        left: Float,
        width: Float,
        continuationContext: ContinuationContext? = null,
        continuationLabel: String? = null,
    ) {
        val textLayout = layout(text, paint, width.toInt())
        var startLine = 0
        while (startLine < textLayout.lineCount) {
            cancellationCheck()
            val availableHeight = CONTENT_BOTTOM - cursorY
            val endLine = findFittingLineEnd(textLayout, startLine, availableHeight)
            if (endLine == startLine) {
                startPage(continuationContext)
                continuationLabel?.let {
                    drawLabel(localization.continued(it))
                    cursorY += 6f
                }
                continue
            }
            drawLineSegment(textLayout, startLine, endLine, left, cursorY)
            cursorY += lineSegmentHeight(textLayout, startLine, endLine)
            startLine = endLine
            if (startLine < textLayout.lineCount) {
                startPage(continuationContext)
                continuationLabel?.let {
                    drawLabel(localization.continued(it))
                    cursorY += 6f
                }
            }
        }
    }

    private fun drawContinuationHeader(context: ContinuationContext) {
        val sourceLayout =
            layout(
                text = context.sourceTitle,
                paint = contextPaint,
                width = CONTENT_WIDTH.toInt(),
                maxLines = CONTEXT_MAX_LINES,
                ellipsize = TextUtils.TruncateAt.END,
            )
        drawLayoutCompletely(sourceLayout, PAGE_MARGIN)
        cursorY += 3f
        val chapterLayout =
            layout(
                text = context.chapter,
                paint = contextChapterPaint,
                width = CONTENT_WIDTH.toInt(),
                maxLines = CONTEXT_MAX_LINES,
                ellipsize = TextUtils.TruncateAt.END,
            )
        drawLayoutCompletely(chapterLayout, PAGE_MARGIN)
        cursorY += 9f
        drawRule(0f, 10f)
    }

    private fun startPage(context: ContinuationContext? = null) {
        cancellationCheck()
        finishPage()
        pageNumber++
        currentPage =
            document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
        canvas = requireNotNull(currentPage).canvas
        canvas.drawColor(PAGE_BACKGROUND)
        cursorY = CONTENT_TOP
        if (pageNumber > 1) {
            canvas.drawText(KAIRO_MASTHEAD, PAGE_MARGIN, cursorY + RUNNING_HEADER_BASELINE, mastheadPaint)
            cursorY += RUNNING_HEADER_HEIGHT
            context?.let(::drawContinuationHeader)
        }
    }

    private fun finishPage() {
        val page = currentPage ?: return
        canvas.drawLine(PAGE_MARGIN, FOOTER_RULE_Y, PAGE_WIDTH - PAGE_MARGIN, FOOTER_RULE_Y, rulePaint)
        val footer = localization.pageNumber(pageNumber)
        canvas.drawText(
            footer,
            PAGE_WIDTH - PAGE_MARGIN - footerPaint.measureText(footer),
            FOOTER_TEXT_Y,
            footerPaint,
        )
        document.finishPage(page)
        currentPage = null
    }

    private fun ensureSpace(
        requestedHeight: Float,
        context: ContinuationContext? = null,
    ) {
        if (CONTENT_BOTTOM - cursorY < requestedHeight) startPage(context)
    }

    private fun drawLabel(label: String) {
        ensureSpace(LABEL_HEIGHT + 2f)
        canvas.drawText(label, PAGE_MARGIN, cursorY + LABEL_BASELINE, labelPaint)
        cursorY += LABEL_HEIGHT
    }

    private fun drawRule(
        topGap: Float,
        bottomGap: Float,
    ) {
        cursorY += topGap
        ensureSpace(bottomGap + 1f)
        canvas.drawLine(PAGE_MARGIN, cursorY, PAGE_MARGIN + CONTENT_WIDTH, cursorY, rulePaint)
        cursorY += bottomGap
    }

    private fun drawLayoutCompletely(
        textLayout: StaticLayout,
        left: Float,
    ) {
        canvas.withTranslation(left, cursorY) { textLayout.draw(this) }
        cursorY += textLayout.height
    }

    private fun drawLineSegment(
        textLayout: StaticLayout,
        startLine: Int,
        endLine: Int,
        left: Float,
        top: Float,
    ) {
        val height = lineSegmentHeight(textLayout, startLine, endLine)
        canvas.withClip(left, top, left + textLayout.width, top + height) {
            translate(left, top - textLayout.getLineTop(startLine))
            textLayout.draw(this)
        }
    }

    private fun lineSegmentHeight(
        textLayout: StaticLayout,
        startLine: Int,
        endLine: Int,
    ): Float =
        max(0, textLayout.getLineBottom(endLine - 1) - textLayout.getLineTop(startLine)).toFloat()

    private fun findFittingLineEnd(
        textLayout: StaticLayout,
        startLine: Int,
        availableHeight: Float,
    ): Int {
        if (availableHeight <= 0f) return startLine
        val startTop = textLayout.getLineTop(startLine)
        var endLine = startLine
        while (endLine < textLayout.lineCount) {
            val candidateHeight = textLayout.getLineBottom(endLine) - startTop
            if (candidateHeight > availableHeight) break
            endLine++
        }
        return endLine
    }

    private fun layout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int = Int.MAX_VALUE,
        ellipsize: TextUtils.TruncateAt? = null,
    ): StaticLayout {
        val safeText = text.ifEmpty { " " }
        val builder =
            StaticLayout.Builder
                .obtain(safeText, 0, safeText.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(0f, (paint as? PdfTextPaint)?.lineSpacingMultiplier ?: DEFAULT_LINE_SPACING)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setMaxLines(maxLines)
                .apply { if (ellipsize != null) setEllipsize(ellipsize) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        } else {
            builder.setLegacyHighQualityBreakStrategy()
        }
        return builder.build()
    }

    // New SDK annotations omit this equal-valued API 23-compatible Layout alias.
    @SuppressLint("WrongConstant")
    private fun StaticLayout.Builder.setLegacyHighQualityBreakStrategy() {
        setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
    }

    private fun textPaint(
        size: Float,
        color: Int,
        typeface: Typeface,
        lineSpacingMultiplier: Float = 1.08f,
    ): PdfTextPaint =
        PdfTextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
            this.lineSpacingMultiplier = lineSpacingMultiplier
        }

    private fun highlightAccent(color: HighlightColor): Int =
        when (color) {
            HighlightColor.YELLOW -> YELLOW_ACCENT
            HighlightColor.BLUE -> BLUE_ACCENT
            HighlightColor.GREEN -> GREEN_ACCENT
            HighlightColor.PINK -> PINK_ACCENT
        }

    private fun passageBackground(color: HighlightColor): Int =
        when (color) {
            HighlightColor.YELLOW -> YELLOW_BACKGROUND
            HighlightColor.BLUE -> BLUE_BACKGROUND
            HighlightColor.GREEN -> GREEN_BACKGROUND
            HighlightColor.PINK -> PINK_BACKGROUND
        }

    private data class ContinuationContext(val sourceTitle: String, val chapter: String,)

    private class PdfTextPaint(flags: Int) : TextPaint(flags) {
        var lineSpacingMultiplier: Float = 1f
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val PAGE_MARGIN = 48f
        const val CONTENT_WIDTH = PAGE_WIDTH - PAGE_MARGIN * 2f
        const val CONTENT_TOP = 42f
        const val CONTENT_BOTTOM = 789f
        const val FOOTER_RULE_Y = 802f
        const val FOOTER_TEXT_Y = 821f
        const val LOGO_SIZE = 34f
        const val KAIRO_MASTHEAD = "KAIRO"
        const val AUTHOR_SEPARATOR = ", "
        const val SOURCE_MINIMUM_SPACE = 86f
        const val ENTRY_MINIMUM_SPACE = 105f
        const val SOURCE_RULE_TOP_GAP = 18f
        const val SOURCE_RULE_BOTTOM_GAP = 20f
        const val LABEL_HEIGHT = 12f
        const val LABEL_BASELINE = 9.5f
        const val PASSAGE_HORIZONTAL_INSET = 18f
        const val PASSAGE_TOP_PADDING = 13f
        const val PASSAGE_BOTTOM_PADDING = 14f
        const val PASSAGE_LABEL_GAP = 7f
        const val PASSAGE_CORNER_RADIUS = 8f
        const val PASSAGE_ACCENT_WIDTH = 4f
        const val PASSAGE_ACCENT_RADIUS = 2f
        const val RUNNING_HEADER_BASELINE = 12f
        const val RUNNING_HEADER_HEIGHT = 24f
        const val CONTEXT_MAX_LINES = 2
        const val DEFAULT_LINE_SPACING = 1.08f

        val PAGE_BACKGROUND = Color.rgb(250, 248, 242)
        val PAGE_TEXT = Color.rgb(37, 35, 31)
        val MUTED_TEXT = Color.rgb(92, 88, 80)
        val FOOTER_TEXT = Color.rgb(112, 106, 96)
        val PRIMARY = Color.rgb(95, 90, 18)
        val RULE = Color.rgb(215, 208, 195)
        val YELLOW_ACCENT = Color.rgb(171, 137, 16)
        val BLUE_ACCENT = Color.rgb(70, 101, 145)
        val GREEN_ACCENT = Color.rgb(75, 112, 69)
        val PINK_ACCENT = Color.rgb(163, 77, 108)
        val YELLOW_BACKGROUND = Color.rgb(244, 236, 194)
        val BLUE_BACKGROUND = Color.rgb(224, 232, 242)
        val GREEN_BACKGROUND = Color.rgb(226, 236, 220)
        val PINK_BACKGROUND = Color.rgb(243, 224, 231)
    }
}
