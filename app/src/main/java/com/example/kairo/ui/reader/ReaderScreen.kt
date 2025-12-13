package com.example.kairo.ui.reader

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairo.core.model.Book
import com.example.kairo.core.model.Token
import com.example.kairo.core.model.TokenType
import com.example.kairo.core.model.nearestWordIndex
import com.example.kairo.ui.theme.MerriweatherFontFamily

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
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit,
    onChapterChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val chapterIndex = uiState.chapterIndex
    val focusIndex = uiState.focusIndex
    val chapter = book.chapters.getOrNull(chapterIndex)

    // Get paragraphs from pre-computed ChapterData (already done off-thread)
    val paragraphs = uiState.chapterData?.paragraphs ?: emptyList()
    val tokens = uiState.chapterData?.tokens ?: emptyList()
    val firstWordIndex = uiState.chapterData?.firstWordIndex ?: -1

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

    // Track previous values to detect user-initiated changes vs initial load
    var lastScrolledFocus by remember { mutableStateOf(focusParagraphIndex) }

    // Scroll instantly when focus changes (e.g., returning from RSVP)
    LaunchedEffect(focusParagraphIndex, listStateKey) {
        if (focusParagraphIndex != lastScrolledFocus && paragraphs.isNotEmpty()) {
            listState.scrollToItem(focusParagraphIndex)
            lastScrolledFocus = focusParagraphIndex
        }
    }

    // Chapter list bottom sheet state
    var showChapterList by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    if (showChapterList) {
        ModalBottomSheet(
            onDismissRequest = { showChapterList = false },
            sheetState = sheetState
        ) {
            ChapterListSheet(
                book = book,
                currentChapterIndex = chapterIndex,
                onChapterSelected = { index ->
                    onChapterChange(index)
                    showChapterList = false
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with book info and navigation
        ReaderHeader(
            book = book,
            chapterIndex = chapterIndex,
            chapterTitle = chapter?.title,
            onShowChapterList = { showChapterList = true },
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
                    key = { index, paragraph -> "${chapterIndex}_${paragraph.startIndex}" }
                ) { paragraphIndex, paragraph ->
                    ParagraphRow(
                        paragraph = paragraph,
                        focusIndex = focusIndex,
                        fontSizeSp = fontSizeSp,
                        onFocusChange = onFocusChange,
                        onStartRsvp = { tokenIndex ->
                            if (firstWordIndex == -1) return@ParagraphRow
                            val safeIndex = tokens.nearestWordIndex(tokenIndex)
                            onStartRsvp(safeIndex)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (firstWordIndex == -1) {
                    Toast.makeText(context, "No readable words in this chapter yet.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val safeIndex = tokens.nearestWordIndex(focusIndex)
                onStartRsvp(safeIndex)
            },
            enabled = !uiState.isLoading && firstWordIndex != -1
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start RSVP at focus word")
        }
    }
}

@Composable
private fun ReaderHeader(
    book: Book,
    chapterIndex: Int,
    chapterTitle: String?,
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
            IconButton(onClick = onShowChapterList) {
                Icon(Icons.Default.List, contentDescription = "Chapter List")
            }
            IconButton(
                onClick = { onChapterChange((chapterIndex - 1).coerceAtLeast(0)) },
                enabled = chapterIndex > 0
            ) {
                Icon(
                    Icons.Default.ArrowBack,
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
                    Icons.Default.ArrowForward,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParagraphRow(
    paragraph: Paragraph,
    focusIndex: Int,
    fontSizeSp: Float,
    onFocusChange: (Int) -> Unit,
    onStartRsvp: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        paragraph.tokens.forEachIndexed { localIndex, token ->
            val globalIndex = paragraph.startIndex + localIndex
            
            // Spacing logic
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

            TokenChip(
                token = token,
                isFocus = globalIndex == focusIndex,
                fontSizeSp = fontSizeSp,
                needsSpaceBefore = needsSpaceBefore,
                onClick = { onFocusChange(globalIndex) },
                onStartRsvp = { onStartRsvp(globalIndex) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TokenChip(
    token: Token,
    isFocus: Boolean,
    fontSizeSp: Float,
    needsSpaceBefore: Boolean,
    onClick: () -> Unit,
    onStartRsvp: () -> Unit
) {
    val baseStyle = TextStyle(
        fontFamily = MerriweatherFontFamily,
        fontSize = fontSizeSp.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
    // Focus word: same size, just different color and underline for clean alignment
    val style = if (isFocus) {
        baseStyle.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        baseStyle
    }

    Row {
        if (needsSpaceBefore) {
            Spacer(modifier = Modifier.width(4.dp))
        }

        Text(
            text = token.text,
            style = style,
            textDecoration = if (isFocus) TextDecoration.Underline else TextDecoration.None,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        // Tap on non-focus word: select it
                        // Tap on focus word: start RSVP (keep existing behavior)
                        if (isFocus) onStartRsvp() else onClick()
                    },
                    onLongClick = {
                        // Long press on any word: select it and start RSVP
                        if (!isFocus) onClick()
                        onStartRsvp()
                    }
                )
                .padding(vertical = 2.dp)
        )
    }
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
