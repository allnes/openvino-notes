package com.itlab.notes.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.itlab.notes.ui.notes.NoteItemUi

class EditorViewModel(
    initialNote: NoteItemUi,
) {
    private val noteId: String = initialNote.id
    private val folderId: String? = initialNote.folderId
    private val tags: Set<String> = initialNote.tags
    private val summary: String? = initialNote.summary

    var title: String by mutableStateOf(initialNote.title)
        private set

    var content: String by mutableStateOf(initialNote.content)
        private set

    fun onTitleChange(newTitle: String) {
        title = newTitle
    }

    fun onContentChange(newContent: String) {
        content = newContent
    }

    fun buildUpdatedNote(): NoteItemUi =
        NoteItemUi(
            id = noteId,
            title = title,
            content = content,
            folderId = folderId,
            tags = tags,
            summary = summary,
        )
}
