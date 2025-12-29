package com.example.kairo.core.tokenization

import com.example.kairo.core.tokenization.cjk.CjkTokenizer
import com.example.kairo.core.tokenization.rtl.RtlTokenizer
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizerRegistryTest {
    @Test
    fun resolvesCjkTokenizerForCjkLanguageTags() {
        assertTrue(TokenizerRegistry.resolve("ja") is CjkTokenizer)
        assertTrue(TokenizerRegistry.resolve("zh-Hans") is CjkTokenizer)
        assertTrue(TokenizerRegistry.resolve("ko-KR") is CjkTokenizer)
    }

    @Test
    fun resolvesDefaultTokenizerForEnglish() {
        assertTrue(TokenizerRegistry.resolve("en") !is CjkTokenizer)
        assertTrue(TokenizerRegistry.resolve(null) !is CjkTokenizer)
    }

    @Test
    fun resolvesRtlTokenizerForRtlLanguages() {
        assertTrue(TokenizerRegistry.resolve("ar") is RtlTokenizer)
        assertTrue(TokenizerRegistry.resolve("he") is RtlTokenizer)
    }
}
