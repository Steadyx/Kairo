package com.kairo.reader.ui.settings

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.kairo.reader.core.model.SavedTheme
import com.kairo.reader.core.model.ThemeDesign
import com.kairo.reader.core.model.decodeThemeDesign
import com.kairo.reader.data.preferences.ThemeLibraryCodec
import org.json.JSONObject

internal data class ThemeStudioDraft(val design: ThemeDesign, val saved: List<SavedTheme>) {
    fun encode(): String = JSONObject().apply {
        put("design", design.encode())
        put("saved", ThemeLibraryCodec.encode(saved))
    }.toString()
}

@Stable
internal class ThemeStudioState(initial: ThemeStudioDraft, previous: List<ThemeStudioDraft> = emptyList(), initialRevision: Int = 0,) {
    var draft by mutableStateOf(initial)
        private set
    var revision by mutableIntStateOf(initialRevision)
        private set
    var locked by mutableStateOf(false)
    private val history = mutableStateListOf<ThemeStudioDraft>().apply { addAll(previous.takeLast(MAX_UNDO)) }
    private var continuousEdit = false
    private var recordedEdit = false
    val canUndo: Boolean get() = history.isNotEmpty() && !locked

    fun beginEdit() {
        continuousEdit = true
    }
    fun endEdit() {
        continuousEdit = false
        recordedEdit = false
    }

    fun change(next: ThemeStudioDraft, resetEditor: Boolean = false) {
        if (locked) return
        if (next == draft) {
            if (resetEditor) {
                endEdit()
                revision++
            }
            return
        }
        if (!continuousEdit || !recordedEdit) {
            if (history.size == MAX_UNDO) history.removeAt(0)
            history.add(draft)
        }
        recordedEdit = continuousEdit
        draft = next
        if (resetEditor) {
            endEdit()
            revision++
        }
    }

    fun design(next: ThemeDesign, resetEditor: Boolean = false) = change(draft.copy(design = next), resetEditor)

    fun undo() {
        if (!canUndo) return
        draft = history.removeAt(history.lastIndex)
        endEdit()
        revision++
    }

    /** Also clears incomplete local colour input, even if the persisted draft did not change. */
    fun reset(next: ThemeStudioDraft) {
        if (locked) return
        change(next)
        endEdit()
        revision++
    }

    companion object {
        val Saver = listSaver<ThemeStudioState, String>(
            save = { listOf(it.revision.toString(), it.draft.encode()) + it.history.map(ThemeStudioDraft::encode) },
            restore = { saved ->
                val revision = saved.firstOrNull()?.toIntOrNull() ?: 0
                val drafts = saved.drop(1).mapNotNull(::decodeDraft)
                drafts.firstOrNull()?.let { ThemeStudioState(it, drafts.drop(1), revision) }
            },
        )
    }
}

private fun decodeDraft(raw: String): ThemeStudioDraft? = runCatching {
    val obj = JSONObject(raw)
    val design = decodeThemeDesign(obj.getString("design")) ?: return null
    ThemeStudioDraft(design, ThemeLibraryCodec.decode(obj.optString("saved")))
}.getOrNull()

private const val MAX_UNDO = 16
