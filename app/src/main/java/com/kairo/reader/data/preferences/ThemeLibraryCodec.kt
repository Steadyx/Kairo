package com.kairo.reader.data.preferences

import com.kairo.reader.core.model.MAX_SAVED_THEMES
import com.kairo.reader.core.model.MAX_THEME_NAME_LENGTH
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.core.model.decodeThemeDesign
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, versioned storage; a damaged entry does not discard the remaining collection. */
internal object ThemeLibraryCodec {
    fun encode(themes: List<SavedTheme>): String = JSONObject().apply {
        put("version", 1)
        put(
            "themes",
            JSONArray().apply {
                themes.distinctBy { it.id }.take(MAX_SAVED_THEMES).forEach { theme ->
                    put(
                        JSONObject().apply {
                            put("id", theme.id)
                            put("name", theme.name.trim().take(MAX_THEME_NAME_LENGTH))
                            put("design", theme.design.encode())
                        }
                    )
                }
            }
        )
    }.toString()

    fun decode(raw: String?): List<SavedTheme> {
        if (raw.isNullOrBlank() || raw.length > MAX_LIBRARY_LENGTH) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version") != 1) return emptyList()
            val entries = root.optJSONArray("themes") ?: return emptyList()
            buildList<SavedTheme> {
                for (index in 0 until minOf(entries.length(), MAX_SAVED_THEMES)) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val id = entry.optString("id").take(MAX_THEME_NAME_LENGTH)
                    val name = entry.optString("name").trim().take(MAX_THEME_NAME_LENGTH)
                    val design = decodeThemeDesign(entry.optString("design")) ?: continue
                    if (id.isNotBlank() && name.isNotBlank() && none { it.id == id }) add(SavedTheme(id, name, design))
                }
            }
        }.getOrDefault(emptyList())
    }
}

private const val MAX_LIBRARY_LENGTH = 64_000
