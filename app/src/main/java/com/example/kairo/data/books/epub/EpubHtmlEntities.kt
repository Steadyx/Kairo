package com.example.kairo.data.books.epub

import java.util.Locale

internal object EpubHtmlEntities {
    private val namedEntities =
        mapOf(
            "nbsp" to " ",
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "lsquo" to "\u2018",
            "rsquo" to "\u2019",
            "sbquo" to "\u201A",
            "ldquo" to "\u201C",
            "rdquo" to "\u201D",
            "bdquo" to "\u201E",
            "laquo" to "\u00AB",
            "raquo" to "\u00BB",
            "lsaquo" to "\u2039",
            "rsaquo" to "\u203A",
            "ndash" to "\u2013",
            "mdash" to "\u2014",
            "hellip" to "\u2026",
            "middot" to "\u00B7",
            "bull" to "\u2022",
            "deg" to "\u00B0",
            "copy" to "\u00A9",
            "reg" to "\u00AE",
            "trade" to "\u2122",
            "sect" to "\u00A7",
            "para" to "\u00B6",
            "dagger" to "\u2020",
            "Dagger" to "\u2021",
            "permil" to "\u2030",
            "prime" to "\u2032",
            "Prime" to "\u2033",
            "cent" to "\u00A2",
            "pound" to "\u00A3",
            "euro" to "\u20AC",
            "yen" to "\u00A5",
            "curren" to "\u00A4",
            "times" to "\u00D7",
            "divide" to "\u00F7",
            "plusmn" to "\u00B1",
            "minus" to "\u2212",
            "le" to "\u2264",
            "ge" to "\u2265",
            "ne" to "\u2260",
            "asymp" to "\u2248",
            "infin" to "\u221E",
            "sum" to "\u2211",
            "radic" to "\u221A",
            "frac14" to "\u00BC",
            "frac12" to "\u00BD",
            "frac34" to "\u00BE",
            "shy" to "\u00AD",
            "thinsp" to "\u2009",
            "ensp" to "\u2002",
            "emsp" to "\u2003",
            "hairsp" to "\u200A",
            "zwnj" to "",
            "zwj" to "",
            "agrave" to "\u00E0",
            "aacute" to "\u00E1",
            "acirc" to "\u00E2",
            "atilde" to "\u00E3",
            "auml" to "\u00E4",
            "aring" to "\u00E5",
            "aelig" to "\u00E6",
            "ccedil" to "\u00E7",
            "egrave" to "\u00E8",
            "eacute" to "\u00E9",
            "ecirc" to "\u00EA",
            "euml" to "\u00EB",
            "igrave" to "\u00EC",
            "iacute" to "\u00ED",
            "icirc" to "\u00EE",
            "iuml" to "\u00EF",
            "eth" to "\u00F0",
            "ntilde" to "\u00F1",
            "ograve" to "\u00F2",
            "oacute" to "\u00F3",
            "ocirc" to "\u00F4",
            "otilde" to "\u00F5",
            "ouml" to "\u00F6",
            "oslash" to "\u00F8",
            "ugrave" to "\u00F9",
            "uacute" to "\u00FA",
            "ucirc" to "\u00FB",
            "uuml" to "\u00FC",
            "yacute" to "\u00FD",
            "thorn" to "\u00FE",
            "yuml" to "\u00FF",
            "Agrave" to "\u00C0",
            "Aacute" to "\u00C1",
            "Acirc" to "\u00C2",
            "Atilde" to "\u00C3",
            "Auml" to "\u00C4",
            "Aring" to "\u00C5",
            "AElig" to "\u00C6",
            "Ccedil" to "\u00C7",
            "Egrave" to "\u00C8",
            "Eacute" to "\u00C9",
            "Ecirc" to "\u00CA",
            "Euml" to "\u00CB",
            "Igrave" to "\u00CC",
            "Iacute" to "\u00CD",
            "Icirc" to "\u00CE",
            "Iuml" to "\u00CF",
            "ETH" to "\u00D0",
            "Ntilde" to "\u00D1",
            "Ograve" to "\u00D2",
            "Oacute" to "\u00D3",
            "Ocirc" to "\u00D4",
            "Otilde" to "\u00D5",
            "Ouml" to "\u00D6",
            "Oslash" to "\u00D8",
            "Ugrave" to "\u00D9",
            "Uacute" to "\u00DA",
            "Ucirc" to "\u00DB",
            "Uuml" to "\u00DC",
            "Yacute" to "\u00DD",
            "THORN" to "\u00DE",
            "szlig" to "\u00DF",
            "iexcl" to "\u00A1",
            "iquest" to "\u00BF",
            "ordf" to "\u00AA",
            "ordm" to "\u00BA",
            "not" to "\u00AC",
            "macr" to "\u00AF",
            "acute" to "\u00B4",
            "cedil" to "\u00B8",
            "micro" to "\u00B5",
            "sup1" to "\u00B9",
            "sup2" to "\u00B2",
            "sup3" to "\u00B3",
        )

    fun decode(input: String): String {
        if (!input.contains('&')) return input

        val sb = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val ampIndex = input.indexOf('&', index)
            if (ampIndex == -1) {
                sb.append(input, index, input.length)
                break
            }
            sb.append(input, index, ampIndex)
            val semiIndex = input.indexOf(';', ampIndex + 1)
            if (semiIndex == -1) {
                sb.append(input, ampIndex, input.length)
                break
            }
            val entity = input.substring(ampIndex + 1, semiIndex)
            val decoded = decodeEntity(entity)
            if (decoded != null) {
                sb.append(decoded)
            } else {
                sb.append(input, ampIndex, semiIndex + 1)
            }
            index = semiIndex + 1
        }
        return sb.toString()
    }

    private fun decodeEntity(entity: String): String? {
        if (entity.isBlank()) return null
        if (entity[0] == '#') {
            val codePoint =
                if (entity.length > 1 && (entity[1] == 'x' || entity[1] == 'X')) {
                    entity.substring(2).toIntOrNull(16)
                } else {
                    entity.substring(1).toIntOrNull()
                }
            return codePoint?.let(::toCodePointString)
        }
        return namedEntities[entity.lowercase(Locale.ROOT)]
    }

    private fun toCodePointString(codePoint: Int): String? {
        if (codePoint !in 0..0x10FFFF) return null
        return if (codePoint <= Char.MAX_VALUE.code) {
            codePoint.toChar().toString()
        } else {
            String(Character.toChars(codePoint))
        }
    }
}
