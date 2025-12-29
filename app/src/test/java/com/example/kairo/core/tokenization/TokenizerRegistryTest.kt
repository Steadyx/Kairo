package com.example.kairo.core.tokenization

import com.example.kairo.core.tokenization.cjk.CjkTokenizer
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
}
