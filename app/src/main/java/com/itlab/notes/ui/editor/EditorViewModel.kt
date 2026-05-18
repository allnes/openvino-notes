package com.itlab.notes.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.itlab.domain.model.ContentItem
import com.itlab.notes.media.withoutTextItems
import com.itlab.notes.ui.notes.NoteItemUi
import com.itlab.notes.ui.toSingleLineText

class EditorViewModel(
    initialNote: NoteItemUi,
) {
    private val noteId: String = initialNote.id
    private val userId: String = initialNote.userId
    private val folderId: String? = initialNote.folderId
    private var syncedTitle: String = initialNote.title
    private var syncedContent: String = initialNote.content
    private var tags: Set<String> = initialNote.tags
    private var summary: String? = initialNote.summary

    var title: String by mutableStateOf(initialNote.title.toSingleLineText())
        private set

    var content: String by mutableStateOf(initialNote.content)
        private set

    var attachments: List<ContentItem> by mutableStateOf(initialNote.attachments.withoutTextItems())
        private set

    var isFavorite: Boolean by mutableStateOf(initialNote.isFavorite)
        private set

    fun syncFavoriteFromNote(value: Boolean) {
        isFavorite = value
    }

    fun syncGeneratedFields(note: NoteItemUi) {
        tags = note.tags
        summary = note.summary
        isFavorite = note.isFavorite
        if (note.title == title || title == syncedTitle) {
            title = note.title.toSingleLineText()
        }
        syncedTitle = note.title.toSingleLineText()
        if (note.content == content || content == syncedContent) {
            content = note.content
        }
        syncedContent = note.content
    }

    fun onTitleChange(newTitle: String) {
        title = newTitle.toSingleLineText()
    }

    fun onContentChange(newContent: String) {
        content = newContent
    }

    fun addAttachment(item: ContentItem) {
        attachments = attachments + item
    }

    fun addAttachments(items: List<ContentItem>) {
        if (items.isEmpty()) return
        attachments = attachments + items
    }

    fun removeAttachment(id: String) {
        attachments = attachments.filterNot { it.id == id }
    }

    fun buildUpdatedNote(): NoteItemUi =
        NoteItemUi(
            id = noteId,
            userId = userId,
            title = title,
            content = content,
            folderId = folderId,
            attachments = attachments,
            isFavorite = isFavorite,
            tags = tags,
            summary = summary,
        )
}
