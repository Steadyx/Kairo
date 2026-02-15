package com.example.kairo.data.books.mobi

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import com.example.kairo.core.model.BookId
import java.io.File

internal class MobiImageProcessor {
    fun extractImages(
        context: Context,
        bookId: BookId,
        data: ByteArray,
        recordOffsets: List<Int>,
        firstImageIndex: Int,
        coverRecordIndex: Int?,
        textRecordCount: Int,
        coverRecindexCandidates: Set<Int>,
        referencedImageIndices: Set<Int>,
    ): MobiImageExtraction {
        val imagePathByRecordIndex = mutableMapOf<Int, String>()
        val imageDir = File(context.filesDir, "kairo_mobi_assets/${bookId.value}/images")
        val canWriteImages = runCatching { imageDir.mkdirs() || imageDir.exists() }.getOrDefault(false)

        val recordCount = recordOffsets.size
        val resourceBaseIndex =
            when {
                firstImageIndex > 0 && firstImageIndex < recordCount -> firstImageIndex
                textRecordCount > 0 -> (1 + textRecordCount).takeIf { it in recordOffsets.indices } ?: -1
                else -> -1
            }
        val resolvedFirstImageIndex =
            when {
                resourceBaseIndex >= 0 &&
                    MobiBinary.isImageRecord(data, recordOffsets, resourceBaseIndex) -> resourceBaseIndex
                else -> MobiBinary.findFirstImageRecordIndex(data, recordOffsets) ?: -1
            }
        val hasValidStartIndex = resolvedFirstImageIndex >= 0
        val startIndex = if (hasValidStartIndex) resolvedFirstImageIndex else 0

        val recindexBase =
            if (resourceBaseIndex >= 0 &&
                resourceBaseIndex > 0 &&
                !MobiBinary.isImageRecord(data, recordOffsets, resourceBaseIndex - 1)
            ) {
                resourceBaseIndex - 1
            } else if (resourceBaseIndex >= 0) {
                resourceBaseIndex
            } else if (hasValidStartIndex &&
                startIndex > 0 &&
                !MobiBinary.isImageRecord(data, recordOffsets, startIndex - 1)
            ) {
                startIndex - 1
            } else if (hasValidStartIndex) {
                startIndex
            } else {
                -1
            }
        val hasValidRecindexBase = recindexBase >= 0

        val explicitCoverIndices =
            buildExplicitCoverIndices(
                coverRecordIndex = coverRecordIndex,
                recindexBase = recindexBase,
                firstImageIndex = firstImageIndex,
                recordCount = recordCount,
                hasValidRecindexBase = hasValidRecindexBase,
            )
        val htmlCoverCandidateIndices =
            buildHtmlCoverCandidateIndices(
                coverRecindexCandidates = coverRecindexCandidates,
                recindexBase = recindexBase,
                recordCount = recordCount,
                hasValidRecindexBase = hasValidRecindexBase,
            )
        val htmlCoverPreferredIndex =
            resolveHtmlCoverPreferredIndex(
                data = data,
                recordOffsets = recordOffsets,
                coverRecindexCandidates = coverRecindexCandidates,
                recindexBase = recindexBase,
                hasValidRecindexBase = hasValidRecindexBase,
            )
        val coverCandidateIndices =
            buildCoverCandidateIndices(
                coverRecindexCandidates = coverRecindexCandidates,
                startIndex = startIndex,
                recindexBase = recindexBase,
                firstImageIndex = firstImageIndex,
                coverRecordIndex = coverRecordIndex,
                recordCount = recordCount,
                hasValidStartIndex = hasValidStartIndex,
            ).toMutableSet().also { it.addAll(explicitCoverIndices) }
        val filterImages = false
        val neededIndices =
            if (filterImages) {
                buildNeededImageIndices(
                    referencedImageIndices = referencedImageIndices,
                    coverCandidateIndices = coverCandidateIndices,
                    startIndex = startIndex,
                    recindexBase = recindexBase,
                    recordCount = recordCount,
                    hasValidStartIndex = hasValidStartIndex,
                )
            } else {
                emptySet()
            }
        val loopStart = if (filterImages) neededIndices.minOrNull() ?: startIndex else startIndex
        val loopEnd = if (filterImages) neededIndices.maxOrNull() ?: recordOffsets.lastIndex else recordOffsets.lastIndex

        var totalImageBytes = 0L
        var firstImage: ByteArray? = null
        var bestOverall: ByteArray? = null
        var bestOverallScore = 0L
        var bestPortrait: ByteArray? = null
        var bestPortraitScore = 0L
        var explicitCoverImage: ByteArray? = null
        var htmlCoverPreferred: ByteArray? = null
        var htmlCoverCandidate: ByteArray? = null
        var htmlCoverCandidateScore = 0L
        var colorCoverCandidate: ByteArray? = null
        var colorCoverScore = 0f
        var coverCandidate: ByteArray? = null
        var coverCandidateScore = 0L
        var coverPortraitCandidate: ByteArray? = null
        var coverPortraitScore = 0L

        for (index in loopStart..loopEnd) {
            if (filterImages && index !in neededIndices) continue
            val start = recordOffsets[index]
            val end = if (index + 1 < recordOffsets.size) recordOffsets[index + 1] else data.size
            if (start < 0 || end > data.size || end <= start) continue
            val raw = data.copyOfRange(start, end)
            val type = MobiBinary.detectImageType(raw) ?: continue
            val maxSize = if (index in explicitCoverIndices) {
                MobiLimits.MAX_COVER_IMAGE_ENTRY_SIZE
            } else {
                MobiLimits.MAX_IMAGE_ENTRY_SIZE
            }
            if (raw.size > maxSize) continue

            totalImageBytes += raw.size
            if (totalImageBytes > MobiLimits.MAX_TOTAL_IMAGE_SIZE) break

            if (firstImage == null) firstImage = raw

            val dimensions = readImageDimensions(type, raw)
            val score = dimensions?.area ?: raw.size.toLong()
            val isPortrait = dimensions?.isPortrait == true

            if (dimensions != null && isPortrait && dimensions.area >= MobiLimits.MIN_COLOR_COVER_AREA) {
                val saturation = estimateColorScore(raw, dimensions)
                if (saturation != null && saturation > colorCoverScore) {
                    colorCoverScore = saturation
                    colorCoverCandidate = raw
                }
            }
            if (explicitCoverImage == null && index in explicitCoverIndices) {
                explicitCoverImage = raw
            }
            if (htmlCoverPreferred == null && index == htmlCoverPreferredIndex) {
                htmlCoverPreferred = raw
            }
            if (index in htmlCoverCandidateIndices && score > htmlCoverCandidateScore) {
                htmlCoverCandidateScore = score
                htmlCoverCandidate = raw
            }
            if (index in coverCandidateIndices) {
                if (score > coverCandidateScore) {
                    coverCandidateScore = score
                    coverCandidate = raw
                }
                if (isPortrait && score > coverPortraitScore) {
                    coverPortraitScore = score
                    coverPortraitCandidate = raw
                }
            }
            if (score > bestOverallScore) {
                bestOverallScore = score
                bestOverall = raw
            }
            if (isPortrait && score > bestPortraitScore) {
                bestPortraitScore = score
                bestPortrait = raw
            }

            if (canWriteImages) {
                val file = File(imageDir, "img_${index}.${type.extension}")
                val wrote = runCatching { file.outputStream().use { it.write(raw) }; true }.getOrDefault(false)
                if (wrote) {
                    imagePathByRecordIndex[index] =
                        "kairo_mobi_assets/${bookId.value}/images/${file.name}"
                }
            }
        }

        val coverImage =
            if (colorCoverCandidate != null && colorCoverScore >= MobiLimits.MIN_COLOR_SCORE) {
                colorCoverCandidate
            } else {
                explicitCoverImage
                    ?: htmlCoverPreferred
                    ?: htmlCoverCandidate
                    ?: coverPortraitCandidate
                    ?: coverCandidate
                    ?: firstImage
                    ?: bestPortrait
                    ?: bestOverall
            }

        return MobiImageExtraction(
            imagePathByRecordIndex = imagePathByRecordIndex,
            coverImage = coverImage,
            resolvedFirstImageIndex = resolvedFirstImageIndex.takeIf { it >= 0 },
            recindexBase = recindexBase.takeIf { it >= 0 },
        )
    }

    fun rewriteImageSrcs(
        html: String,
        imagePathByRecordIndex: Map<Int, String>,
        recindexBase: Int,
    ): String {
        var updated = html

        val recindexRegex =
            Regex(
                """(<img\b[^>]*?)\s+recindex\s*=\s*['"](\d+)['"]([^>]*>)""",
                RegexOption.IGNORE_CASE,
            )
        updated = recindexRegex.replace(updated) { match ->
            val recindex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val path = resolveImagePath(recindex, imagePathByRecordIndex, recindexBase)
                ?: return@replace match.value
            "${match.groupValues[1]} src=\"$path\"${match.groupValues[3]}"
        }

        val embedRegex =
            Regex(
                """(src\s*=\s*['"])kindle:embed:(\d+)(['"])""",
                RegexOption.IGNORE_CASE,
            )
        updated = embedRegex.replace(updated) { match ->
            val embedIndex = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            val path = resolveImagePath(embedIndex, imagePathByRecordIndex, recindexBase)
                ?: return@replace match.value
            "${match.groupValues[1]}$path${match.groupValues[3]}"
        }

        if (imagePathByRecordIndex.isNotEmpty()) {
            val orderedPaths = imagePathByRecordIndex.toSortedMap().values.toList()
            var fallbackIndex = 0
            val imgRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
            updated = imgRegex.replace(updated) { match ->
                val tag = match.value
                if (tag.contains("recindex", true)) return@replace tag
                if (tag.contains("kindle:embed", true)) return@replace tag
                val src = MobiHtmlUtils.extractAttribute(tag, "src") ?: return@replace tag
                if (src.startsWith("data:", true) ||
                    src.startsWith("http://", true) ||
                    src.startsWith("https://", true) ||
                    src.contains("kairo_mobi_assets/", true)
                ) {
                    return@replace tag
                }
                val path = orderedPaths.getOrNull(fallbackIndex) ?: return@replace tag
                fallbackIndex += 1
                replaceSrcInTag(tag, path)
            }
        }
        return updated
    }

    private fun buildNeededImageIndices(
        referencedImageIndices: Set<Int>,
        coverCandidateIndices: Set<Int>,
        startIndex: Int,
        recindexBase: Int,
        recordCount: Int,
        hasValidStartIndex: Boolean,
    ): Set<Int> {
        val needed = LinkedHashSet<Int>()
        needed.addAll(coverCandidateIndices)
        fun addRecindex(recindex: Int) {
            needed.add(recindex)
            if (recindexBase >= 0) {
                needed.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordCount))
            }
            if (hasValidStartIndex) {
                needed.add(startIndex + recindex)
            }
        }
        referencedImageIndices.forEach(::addRecindex)
        if (needed.isEmpty() && hasValidStartIndex) {
            repeat(MobiLimits.COVER_FALLBACK_IMAGE_SCAN) { offset ->
                needed.add(startIndex + offset)
            }
        }
        return needed
    }

    private fun buildCoverCandidateIndices(
        coverRecindexCandidates: Set<Int>,
        startIndex: Int,
        recindexBase: Int,
        firstImageIndex: Int,
        coverRecordIndex: Int?,
        recordCount: Int,
        hasValidStartIndex: Boolean,
    ): Set<Int> {
        val candidates = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            candidates.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordCount))
        }
        candidates.addAll(
            resolveCoverRecordIndices(
                coverRecordIndex = coverRecordIndex,
                firstImageIndex = firstImageIndex,
                recindexBase = recindexBase,
                recordCount = recordCount,
            ),
        )
        if (candidates.isEmpty() && hasValidStartIndex) {
            repeat(MobiLimits.COVER_FALLBACK_IMAGE_SCAN) { offset ->
                candidates.add(startIndex + offset)
            }
        }
        return candidates
    }

    private fun buildHtmlCoverCandidateIndices(
        coverRecindexCandidates: Set<Int>,
        recindexBase: Int,
        recordCount: Int,
        hasValidRecindexBase: Boolean,
    ): Set<Int> {
        if (coverRecindexCandidates.isEmpty()) return emptySet()
        val indices = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            if (recindex in 0 until recordCount) indices.add(recindex)
            if (hasValidRecindexBase) {
                indices.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordCount))
            }
        }
        return indices
    }

    private fun resolveHtmlCoverPreferredIndex(
        data: ByteArray,
        recordOffsets: List<Int>,
        coverRecindexCandidates: Set<Int>,
        recindexBase: Int,
        hasValidRecindexBase: Boolean,
    ): Int? {
        if (coverRecindexCandidates.isEmpty()) return null
        val seen = LinkedHashSet<Int>()
        coverRecindexCandidates.forEach { recindex ->
            if (recindex >= 0) seen.add(recindex)
            if (hasValidRecindexBase) {
                seen.addAll(resolveRecindexToRecordIndices(recindex, recindexBase, recordOffsets.size))
            }
        }
        for (candidate in seen) {
            if (MobiBinary.isImageRecord(data, recordOffsets, candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun buildExplicitCoverIndices(
        coverRecordIndex: Int?,
        recindexBase: Int,
        firstImageIndex: Int,
        recordCount: Int,
        hasValidRecindexBase: Boolean,
    ): Set<Int> {
        val indices = LinkedHashSet<Int>()
        if (coverRecordIndex == null) return indices

        indices.addAll(
            resolveCoverRecordIndices(
                coverRecordIndex = coverRecordIndex,
                firstImageIndex = firstImageIndex,
                recindexBase = if (hasValidRecindexBase) recindexBase else -1,
                recordCount = recordCount,
            ),
        )
        if (indices.isEmpty() && coverRecordIndex in 0 until recordCount) {
            indices.add(coverRecordIndex)
        }
        return indices
    }

    private fun resolveCoverRecordIndices(
        coverRecordIndex: Int?,
        firstImageIndex: Int,
        recindexBase: Int,
        recordCount: Int,
    ): Set<Int> {
        if (coverRecordIndex == null) return emptySet()
        val candidates = LinkedHashSet<Int>()
        if (coverRecordIndex in 0 until recordCount) {
            candidates.add(coverRecordIndex)
        }
        if (firstImageIndex > 0) {
            val zeroBased = firstImageIndex + coverRecordIndex
            if (zeroBased in 0 until recordCount) {
                candidates.add(zeroBased)
            }
            if (coverRecordIndex > 0) {
                val oneBased = firstImageIndex + coverRecordIndex - 1
                if (oneBased in 0 until recordCount) {
                    candidates.add(oneBased)
                }
            }
        }
        if (recindexBase >= 0) {
            candidates.addAll(resolveRecindexToRecordIndices(coverRecordIndex, recindexBase, recordCount))
        }
        return candidates
    }

    private fun resolveRecindexToRecordIndices(
        recindex: Int,
        recindexBase: Int,
        recordCount: Int,
    ): Set<Int> {
        if (recindexBase < 0) return emptySet()
        val resolved = LinkedHashSet<Int>()
        val zeroBased = recindexBase + recindex
        if (zeroBased in 0 until recordCount) {
            resolved.add(zeroBased)
        }
        if (recindex > 0) {
            val oneBased = recindexBase + recindex - 1
            if (oneBased in 0 until recordCount) {
                resolved.add(oneBased)
            }
        }
        return resolved
    }

    private fun replaceSrcInTag(
        tag: String,
        src: String,
    ): String {
        val srcRegex = Regex("""\bsrc\s*=\s*(?:'[^']*'|"[^"]*"|[^\s>]+)""", RegexOption.IGNORE_CASE)
        return if (srcRegex.containsMatchIn(tag)) {
            srcRegex.replace(tag) { "src=\"$src\"" }
        } else {
            val (prefix, suffix) = if (tag.endsWith("/>")) tag.dropLast(2) to "/>" else tag.dropLast(1) to ">"
            "$prefix src=\"$src\"$suffix"
        }
    }

    private fun resolveImagePath(
        index: Int,
        imagePathByRecordIndex: Map<Int, String>,
        recindexBase: Int,
    ): String? {
        imagePathByRecordIndex[index]?.let { return it }
        if (recindexBase >= 0) {
            imagePathByRecordIndex[recindexBase + index]?.let { return it }
            if (index > 0) {
                imagePathByRecordIndex[recindexBase + index - 1]?.let { return it }
            }
        }
        return null
    }

    private fun readImageDimensions(
        type: MobiImageType,
        bytes: ByteArray,
    ): MobiImageDimensions? =
        when (type.extension) {
            "jpg" -> readJpegDimensions(bytes)
            "png" -> readPngDimensions(bytes)
            "gif" -> readGifDimensions(bytes)
            "bmp" -> readBmpDimensions(bytes)
            else -> null
        }

    private fun readPngDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < 24) return null
        val width = MobiBinary.readInt(bytes, 16)
        val height = MobiBinary.readInt(bytes, 20)
        return if (width > 0 && height > 0) MobiImageDimensions(width, height) else null
    }

    private fun readGifDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < 10) return null
        val width = MobiBinary.readLittleEndianShort(bytes, 6)
        val height = MobiBinary.readLittleEndianShort(bytes, 8)
        return if (width > 0 && height > 0) MobiImageDimensions(width, height) else null
    }

    private fun readBmpDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < 26) return null
        val width = MobiBinary.readLittleEndianInt(bytes, 18)
        val height = MobiBinary.readLittleEndianInt(bytes, 22)
        val absoluteHeight = if (height < 0) -height else height
        return if (width > 0 && absoluteHeight > 0) MobiImageDimensions(width, absoluteHeight) else null
    }

    private fun readJpegDimensions(bytes: ByteArray): MobiImageDimensions? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var index = 2
        while (index + 1 < bytes.size) {
            if (bytes[index] != 0xFF.toByte()) {
                index++
                continue
            }
            while (index < bytes.size && bytes[index] == 0xFF.toByte()) index++
            if (index >= bytes.size) break
            val marker = bytes[index].toInt() and 0xFF
            index++
            if (marker == 0xD8 || marker == 0xD9) continue
            if (index + 1 >= bytes.size) break
            val length = ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
            if (length < 2) return null
            if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                if (index + 7 >= bytes.size) return null
                val height = ((bytes[index + 3].toInt() and 0xFF) shl 8) or (bytes[index + 4].toInt() and 0xFF)
                val width = ((bytes[index + 5].toInt() and 0xFF) shl 8) or (bytes[index + 6].toInt() and 0xFF)
                return if (width > 0 && height > 0) MobiImageDimensions(width, height) else null
            }
            index += length
        }
        return null
    }

    private fun estimateColorScore(
        bytes: ByteArray,
        dimensions: MobiImageDimensions,
    ): Float? {
        val sampleMax = 72
        val sampleSize =
            if (dimensions.width > sampleMax || dimensions.height > sampleMax) {
                maxOf(1, minOf(dimensions.width / sampleMax, dimensions.height / sampleMax))
            } else {
                1
            }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            bitmap.recycle()
            return null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        var total = 0f
        var count = 0
        val hsv = FloatArray(3)
        val step = if (pixels.size > 4096) 2 else 1
        var index = 0
        while (index < pixels.size) {
            Color.colorToHSV(pixels[index], hsv)
            total += hsv[1]
            count += 1
            index += step
        }
        return if (count > 0) total / count else null
    }
}
