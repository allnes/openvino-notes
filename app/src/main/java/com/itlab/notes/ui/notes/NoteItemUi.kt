package com.itlab.notes.ui.notes

data class NoteItemUi(
    val id: String,
    val title: String,
    val content: String,
    val folderId: String? = null,
    val tags: Set<String> = emptySet(),
    val summary: String? = null,
)
