package com.example.kairo.data.books

internal sealed interface EpubMarkupNode

internal data class EpubMarkupTextNode(
    val text: String,
) : EpubMarkupNode

internal data class EpubMarkupElementNode(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<EpubMarkupNode> = mutableListOf(),
) : EpubMarkupNode

internal data class EpubMarkupDocument(
    val children: MutableList<EpubMarkupNode> = mutableListOf(),
)
