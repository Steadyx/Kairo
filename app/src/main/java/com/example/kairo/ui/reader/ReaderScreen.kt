package com.example.kairo.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.ui.theme.MerriweatherFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Opening quotes/brackets attach to the NEXT word (no space after them)
private val openingQuotes = setOf('"', '\u201C', '\u2018', '(', '[', '{')
// Closing punctuation attaches to the PREVIOUS word (no space before them)
private val closingPunctuation = setOf('.', ',', ';', ':', '!', '?', '"', '\u201D', '\u2019', ')', ']', '}', '\u2014', '\u2013', '\u2026')

/**
 * Main reader screen - can be called directly with ViewModel state.
 * 
 * Usage with ViewModel:
 * ```
 * val uiState by viewModel.uiState.collectAsState()
 * ReaderScreen(
 *     book = book,
 *     uiState = uiState,
 *     fontSizeSp = 18f,
 *     invertedScroll = false,
 *     onFocusChange = viewModel::setFocusIndex,
 *     onStartRsvp = { index -> navController.navigate("rsvp/$index") },
 *     onChapterChange = viewModel::loadChapter
 * )
 * ```
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    uiState: ReaderUiState,
    fontSizeSp: Float,
    invertedScroll: Boolean,
    focusModeEnabled: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
    onChapterChange: (Int) -> Unit
) {
    val chapterIndex = uiState.chapterIndex
    val focusIndex = uiState.focusIndex
    val chapter = book.chapters.getOrNull(chapterIndex)

    // Get paragraphs from pre-computed ChapterData (already done off-thread)
    val paragraphs = uiState.chapterData?.paragraphs ?: emptyList()
    val tokens = uiState.chapterData?.tokens ?: emptyList()
    val firstWordIndex = uiState.chapterData?.firstWordIndex ?: -1
    val onSafeFocusChange = remember(tokens, onFocusChange) {
        { index: Int ->
            if (tokens.isNotEmpty()) {
                onFocusChange(tokens.nearestWordIndex(index))
            }
        }
    }

    // Find which paragraph contains the focus index
    val focusParagraphIndex = remember(focusIndex, paragraphs) {
        if (paragraphs.isEmpty()) 0
        else paragraphs.indexOfFirst { paragraph ->
            val endIndex = paragraph.startIndex + paragraph.tokens.size - 1
            focusIndex in paragraph.startIndex..endIndex
        }.coerceAtLeast(0)
    }

    // Use a key that changes when we need to reset the list position
    // This forces a new LazyListState with the correct initial position
    val listStateKey = remember(chapterIndex, uiState.chapterData) {
        "$chapterIndex-${uiState.chapterData?.hashCode() ?: 0}"
    }

    val listState = key(listStateKey) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = focusParagraphIndex
        )
    }

    // Scroll instantly when focus changes (e.g., returning from RSVP)
    LaunchedEffect(focusParagraphIndex, listStateKey) {
        if (paragraphs.isNotEmpty() && listState.firstVisibleItemIndex != focusParagraphIndex) {
            listState.scrollToItem(focusParagraphIndex)
        }
    }

    // Chapter list bottom sheet state
    val showChapterList = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    if (showChapterList.value) {
        ModalBottomSheet(
            onDismissRequest = { showChapterList.value = false },
            sheetState = sheetState
        ) {
            ChapterListSheet(
                book = book,
                currentChapterIndex = chapterIndex,
                onChapterSelected = { index ->
                    onChapterChange(index)
                    showChapterList.value = false
                }
            )
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val isRsvpEnabled = !uiState.isLoading && firstWordIndex != -1 && tokens.isNotEmpty()
    val progressPercent = remember(tokens, focusIndex) {
        if (tokens.size < 2) 0
        else {
            val safeFocus = tokens.nearestWordIndex(focusIndex).coerceIn(0, tokens.lastIndex)
            ((safeFocus.toFloat() / tokens.lastIndex.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }
    }
    val progressFraction by remember(progressPercent) {
        derivedStateOf { (progressPercent / 100f).coerceIn(0f, 1f) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            // Header with book info and navigation
            ReaderHeader(
                book = book,
                chapterIndex = chapterIndex,
                chapterTitle = chapter?.title,
                focusModeEnabled = focusModeEnabled,
                onFocusModeEnabledChange = onFocusModeEnabledChange,
                onShowChapterList = { showChapterList.value = true },
                onChapterChange = onChapterChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Show loading indicator or content
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (paragraphs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No content in this chapter",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // LAZY paragraph-based rendering
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp) // Paragraph spacing
                ) {
                    itemsIndexed(
                        items = paragraphs,
                        key = { _, paragraph -> "${chapterIndex}_${paragraph.startIndex}" }
                    ) { _, paragraph ->
                        ParagraphText(
                            paragraph = paragraph,
                            focusIndex = focusIndex,
                            fontSizeSp = fontSizeSp,
                            onFocusChange = onSafeFocusChange,
                            onStartRsvp = { tokenIndex ->
                                if (!isRsvpEnabled) return@ParagraphText
                                onStartRsvp(tokens.nearestWordIndex(tokenIndex))
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isRsvpEnabled,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            var dragAccumulator by remember { mutableFloatStateOf(0f) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pointerInput(focusParagraphIndex) {
                        detectTapGestures(
                            onLongPress = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(focusParagraphIndex)
                                }
                            }
                        )
                    }
                    .pointerInput(tokens, focusIndex, invertedScroll) {
                        val thresholdPx = 22f
                        var gestureFocusIndex = 0
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragAccumulator = 0f
                                gestureFocusIndex = if (tokens.isNotEmpty()) {
                                    tokens.nearestWordIndex(focusIndex).coerceIn(0, tokens.lastIndex)
                                } else {
                                    0
                                }
                            },
                            onDragEnd = { dragAccumulator = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (tokens.isEmpty()) return@detectVerticalDragGestures
                                dragAccumulator += dragAmount
                                val steps = (dragAccumulator / thresholdPx).toInt()
                                if (steps == 0) return@detectVerticalDragGestures

                                // Swipe up (negative drag) moves forward by default.
                                val rawDirection = -steps
                                val effectiveDirection = if (invertedScroll) -rawDirection else rawDirection
                                val next = tokens.nearestWordIndex(
                                    (gestureFocusIndex + effectiveDirection).coerceIn(0, tokens.lastIndex)
                                )
                                gestureFocusIndex = next
                                onFocusChange(next)
                                dragAccumulator -= steps * thresholdPx
                            }
                        )
                    }
            ) {
                CircularProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.size(76.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                )
                FloatingActionButton(
                    onClick = {
                        val safeIndex = tokens.nearestWordIndex(focusIndex)
                        onStartRsvp(safeIndex)
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start RSVP")
                }
            }
        }
    }
}

@Composable
private fun ReaderHeader(
    book: Book,
    chapterIndex: Int,
    chapterTitle: String?,
    focusModeEnabled: Boolean,
    onFocusModeEnabledChange: (Boolean) -> Unit,
    onShowChapterList: () -> Unit,
    onChapterChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chapterTitle ?: "Chapter ${chapterIndex + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Chapter ${chapterIndex + 1} of ${book.chapters.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { onFocusModeEnabledChange(!focusModeEnabled) }) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = "Focus mode",
                    tint = if (focusModeEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
            IconButton(onClick = onShowChapterList) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chapter List")
            }
            IconButton(
                onClick = { onChapterChange((chapterIndex - 1).coerceAtLeast(0)) },
                enabled = chapterIndex > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Chapter",
                    tint = if (chapterIndex > 0)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = { onChapterChange((chapterIndex + 1).coerceAtMost(book.chapters.lastIndex)) },
                enabled = chapterIndex < book.chapters.lastIndex
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Chapter",
                    tint = if (chapterIndex < book.chapters.lastIndex)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun ParagraphText(
    paragraph: Paragraph,
    focusIndex: Int,
    fontSizeSp: Float,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit
) {
    val baseStyle = TextStyle(
        fontFamily = MerriweatherFontFamily,
        fontSize = fontSizeSp.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
    val primary = MaterialTheme.colorScheme.primary
    val focusStyle = remember(primary) {
        SpanStyle(
            fontWeight = FontWeight.SemiBold,
            color = primary,
            textDecoration = TextDecoration.Underline
        )
    }

    val annotated = remember(paragraph.tokens, paragraph.startIndex, focusIndex, primary) {
        buildAnnotatedString {
            paragraph.tokens.forEachIndexed { localIndex, token ->
                if (token.type == TokenType.PARAGRAPH_BREAK) return@forEachIndexed
                val globalIndex = paragraph.startIndex + localIndex

                val prevToken = if (localIndex > 0) paragraph.tokens[localIndex - 1] else null
                val prevWasOpeningQuote = prevToken?.type == TokenType.PUNCTUATION &&
                    prevToken.text.length == 1 &&
                    openingQuotes.contains(prevToken.text[0])

                val isOpeningPunct = token.type == TokenType.PUNCTUATION &&
                    token.text.length == 1 &&
                    openingQuotes.contains(token.text[0])
                val isClosingPunct = token.type == TokenType.PUNCTUATION &&
                    token.text.length == 1 &&
                    closingPunctuation.contains(token.text[0])

                val needsSpaceBefore = when {
                    localIndex == 0 -> false
                    isClosingPunct -> false
                    isOpeningPunct -> true
                    prevWasOpeningQuote -> false
                    else -> true
                }

                if (needsSpaceBefore) append(" ")

                val start = length
                append(token.text)
                val end = length

                addStringAnnotation(tag = "tokenIndex", annotation = globalIndex.toString(), start = start, end = end)
                if (globalIndex == focusIndex) addStyle(focusStyle, start, end)
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = baseStyle,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(annotated, focusIndex) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position).coerceIn(0, (annotated.length - 1).coerceAtLeast(0))
                        val hit = annotated.getStringAnnotations("tokenIndex", offset, offset).firstOrNull()
                            ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex == focusIndex) onStartRsvp(tokenIndex) else onFocusChange(tokenIndex)
                    },
                    onLongPress = { position ->
                        val layout = layoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position).coerceIn(0, (annotated.length - 1).coerceAtLeast(0))
                        val hit = annotated.getStringAnnotations("tokenIndex", offset, offset).firstOrNull()
                            ?: return@detectTapGestures
                        val tokenIndex = hit.item.toIntOrNull() ?: return@detectTapGestures
                        if (tokenIndex != focusIndex) onFocusChange(tokenIndex)
                        onStartRsvp(tokenIndex)
                    }
                )
            },
        onTextLayout = { layoutResult = it }
    )
}

/**
 * Bottom sheet displaying the table of contents / chapter list.
 * Now expects pre-computed word counts in Chapter model.
 */
@Composable
private fun ChapterListSheet(
    book: Book,
    currentChapterIndex: Int,
    onChapterSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Table of Contents",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = book.chapters,
                key = { index, _ -> index }
            ) { index, chapter ->
                val isCurrentChapter = index == currentChapterIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterSelected(index) }
                        .background(
                            if (isCurrentChapter)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.title ?: "Chapter ${index + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrentChapter)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Use pre-computed word count from Chapter model
                        // If not available, show nothing rather than computing on-the-fly
                        // chapter.wordCount?.let { count ->
                        //     Text(
                        //         text = "$count words",
                        //         style = MaterialTheme.typography.bodySmall,
                        //         color = MaterialTheme.colorScheme.onSurfaceVariant
                        //     )
                        // }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isCurrentChapter)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrentChapter)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (index < book.chapters.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
