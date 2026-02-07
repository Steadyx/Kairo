package com.example.kairo.data.books.epub

import java.util.Locale

internal object EpubPathResolver {
    fun resolvePath(
        baseDir: String,
        relativePath: String,
    ): String {
        if (relativePath.contains("://")) {
            return ""
        }

        val pathToResolve = relativePath.removePrefix("./").trimStart('/', '\\')
        if (baseDir.isEmpty()) {
            return validateNoEscape(pathToResolve)
        }

        val baseParts = baseDir.split("/").filter { it.isNotEmpty() }.toMutableList()
        val relParts = pathToResolve.split("/")

        for (part in relParts) {
            when (part) {
                ".." -> {
                    if (baseParts.isNotEmpty()) {
                        baseParts.removeAt(baseParts.lastIndex)
                    } else {
                        return ""
                    }
                }
                ".", "" -> Unit
                else -> baseParts.add(part)
            }
        }

        return baseParts.joinToString("/")
    }

    fun validateNoEscape(path: String): String {
        val parts = path.split("/")
        var depth = 0
        for (part in parts) {
            when (part) {
                ".." -> {
                    depth--
                    if (depth < 0) return ""
                }
                ".", "" -> Unit
                else -> depth++
            }
        }
        return path.split("/").filter { it != "." && it.isNotEmpty() }.joinToString("/")
    }

    fun normalizeHrefValue(href: String): String {
        val withoutFragments = href.substringBefore('#').substringBefore('?').trim()
        if (withoutFragments.isBlank()) return ""
        return decodeUrlPath(EpubHtmlEntities.decode(withoutFragments)).trim()
    }

    fun resolveZipEntryKey(
        baseDir: String,
        rawHref: String,
        availableEntriesLower: Set<String>,
    ): String? {
        val candidates = buildPathCandidates(rawHref)
        val tryRootFallback = baseDir.isNotBlank() && shouldTryRootFallback(rawHref)
        for (candidate in candidates) {
            val resolvedWithBase = resolvePath(baseDir, candidate)
            val matchedWithBase = matchEntryKey(resolvedWithBase, availableEntriesLower)
            if (matchedWithBase != null) return matchedWithBase

            if (tryRootFallback) {
                val resolvedRoot = resolvePath("", candidate)
                val matchedRoot = matchEntryKey(resolvedRoot, availableEntriesLower)
                if (matchedRoot != null) return matchedRoot
            }
        }
        return null
    }

    fun shouldTryRootFallback(rawHref: String): Boolean {
        val trimmed = rawHref.trim()
        if (trimmed.isBlank()) return false
        return trimmed.startsWith('/') || trimmed.startsWith('\\')
    }

    fun buildPathCandidates(rawHref: String): List<String> {
        val trimmed = rawHref.trim()
        if (trimmed.isBlank()) return emptyList()
        val base = trimmed.substringBefore('#').substringBefore('?').trim()
        if (base.isBlank()) return emptyList()

        val variants = LinkedHashSet<String>()
        fun addVariant(value: String) {
            if (value.isNotBlank()) variants.add(value)
        }

        addVariant(base)
        addVariant(base.replace('\\', '/'))

        val htmlDecoded = EpubHtmlEntities.decode(base)
        addVariant(htmlDecoded)
        addVariant(htmlDecoded.replace('\\', '/'))

        val urlDecoded = decodeUrlPath(htmlDecoded)
        addVariant(urlDecoded)
        addVariant(urlDecoded.replace('\\', '/'))

        addVariant(encodeUrlPath(htmlDecoded))
        if (urlDecoded != htmlDecoded) {
            addVariant(encodeUrlPath(urlDecoded))
        }

        val normalized = LinkedHashSet<String>()
        for (variant in variants) {
            val cleaned =
                variant
                    .trim()
                    .removePrefix("./")
                    .trimStart('/', '\\')
                    .replace('\\', '/')
            if (cleaned.isNotBlank()) normalized.add(cleaned)
        }
        return normalized.toList()
    }

    fun matchEntryKey(
        resolvedPath: String,
        availableEntriesLower: Set<String>,
    ): String? {
        if (resolvedPath.isBlank()) return null
        val lower = resolvedPath.lowercase(Locale.ROOT)
        return if (availableEntriesLower.contains(lower)) lower else null
    }

    fun resolveOpfPath(
        rawPath: String?,
        availableEntriesLower: Set<String>,
        allowFallback: Boolean = true,
    ): String? {
        val candidates =
            if (rawPath.isNullOrBlank()) {
                emptyList()
            } else {
                buildPathCandidates(rawPath).ifEmpty { listOf(normalizeContainerPath(rawPath)) }
            }

        for (candidate in candidates) {
            val resolved = resolvePath("", candidate)
            val matched = matchEntryKey(resolved, availableEntriesLower)
            if (matched != null) return matched
        }

        return if (allowFallback) selectFallbackOpfPath(availableEntriesLower) else null
    }

    fun selectFallbackOpfPath(availableEntriesLower: Set<String>): String? {
        return availableEntriesLower
            .asSequence()
            .filter { it.endsWith(".opf") }
            .sortedWith(
                compareBy(
                    ::opfFallbackPriority,
                    ::pathDepth,
                    { it.length },
                    { it },
                ),
            ).firstOrNull()
    }

    fun opfFallbackPriority(pathLower: String): Int {
        return when (pathLower.substringAfterLast('/')) {
            "content.opf" -> 0
            "package.opf" -> 1
            "book.opf" -> 2
            else -> 3
        }
    }

    fun pathDepth(path: String): Int {
        if (path.isBlank()) return 0
        return path.count { it == '/' } + 1
    }

    fun normalizeIdRef(idref: String): String = idref.trim().removePrefix("#").trim()

    fun normalizeContainerPath(path: String): String {
        var cleaned = decodeUrlPath(EpubHtmlEntities.decode(path)).trim()
        cleaned = cleaned.replace('\\', '/')
        cleaned = cleaned.removePrefix("./")
        cleaned = cleaned.trimStart('/')
        return cleaned
    }

    fun decodeUrlPath(input: String): String {
        if (!input.contains('%')) return input
        val out = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            if (input[index] == '%' && index + 2 < input.length) {
                val decodedBytes = ArrayList<Byte>()
                var cursor = index
                while (cursor + 2 < input.length && input[cursor] == '%') {
                    val hi = input[cursor + 1].digitToIntOrNull(16)
                    val lo = input[cursor + 2].digitToIntOrNull(16)
                    if (hi == null || lo == null) break
                    decodedBytes.add(((hi shl 4) + lo).toByte())
                    cursor += 3
                }
                if (decodedBytes.isNotEmpty()) {
                    out.append(decodedBytes.toByteArray().toString(Charsets.UTF_8))
                    index = cursor
                    continue
                }
            }
            out.append(input[index])
            index += 1
        }
        return out.toString()
    }

    fun encodeUrlPath(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val intVal = byte.toInt() and 0xFF
            if (isUnreservedAscii(intVal)) {
                sb.append(intVal.toChar())
            } else {
                sb.append('%')
                sb.append(intVal.toString(16).uppercase(Locale.ROOT).padStart(2, '0'))
            }
        }
        return sb.toString()
    }

    fun isUnreservedAscii(codePoint: Int): Boolean {
        return (codePoint in 'a'.code..'z'.code) ||
            (codePoint in 'A'.code..'Z'.code) ||
            (codePoint in '0'.code..'9'.code) ||
            codePoint == '-'.code ||
            codePoint == '_'.code ||
            codePoint == '.'.code ||
            codePoint == '~'.code ||
            codePoint == '/'.code
    }
}
