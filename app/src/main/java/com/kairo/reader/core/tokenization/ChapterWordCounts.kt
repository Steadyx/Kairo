package com.kairo.reader.core.tokenization

import com.kairo.reader.core.language.LanguageFamily
import com.kairo.reader.core.language.LanguageFamilyClassifier
import com.kairo.reader.core.model.Chapter
import com.kairo.reader.core.model.countWords

fun usesCjkWordSegmentation(languageTag: String?): Boolean =
    LanguageFamilyClassifier.classify(languageTag).family == LanguageFamily.CJK

fun countChapterWords(chapter: Chapter, languageTag: String?): Int =
    if (usesCjkWordSegmentation(languageTag)) {
        countWords(TokenizerRegistry.resolve(languageTag).tokenize(chapter))
    } else {
        countWords(chapter.plainText)
    }

// Bump when the persisted reading-unit counting policy changes.
const val CHAPTER_WORD_COUNT_VERSION = 1

fun needsChapterWordCountRepair(chapter: Chapter, languageTag: String?): Boolean =
    chapter.wordCountVersion < CHAPTER_WORD_COUNT_VERSION &&
        (chapter.wordCount <= 0 || usesCjkWordSegmentation(languageTag))
