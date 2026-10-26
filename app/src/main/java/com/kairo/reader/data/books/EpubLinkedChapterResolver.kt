package com.kairo.reader.data.books

import com.kairo.reader.data.books.epub.EpubPathResolver

/** Finds linked manifest documents without turning every ZIP entry into a reading chapter. */
internal fun findLinkedAuxiliaryChapters(
    readingPaths: List<String>,
    manifestPaths: Set<String>,
    navigationHrefs: List<Pair<String, String>> = emptyList(),
    readHtml: (String) -> String?,
    contentRewriter: EpubContentRewriter,
): List<String> {
    val visited = readingPaths.toMutableSet()
    val queue = ArrayDeque(readingPaths)
    val auxiliary = mutableListOf<String>()
    fun addTarget(baseDir: String, href: String) {
        val target = EpubPathResolver.resolveZipEntryKey(baseDir, href, manifestPaths) ?: return
        if (visited.add(target)) {
            auxiliary.add(target)
            queue.addLast(target)
        }
    }
    navigationHrefs.forEach { (baseDir, href) -> addTarget(baseDir, href) }
    while (queue.isNotEmpty()) {
        val path = queue.removeFirst()
        val html = readHtml(path) ?: continue
        val baseDir = path.substringBeforeLast('/', "")
        contentRewriter.extractAnchorHrefs(html).forEach { href -> addTarget(baseDir, href) }
    }
    return auxiliary
}
