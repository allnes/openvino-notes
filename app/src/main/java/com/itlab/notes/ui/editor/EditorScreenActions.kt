package com.itlab.notes.ui.editor

import com.itlab.notes.ui.notes.NoteItemUi

data class EditorScreenActions(
    val onBack: () -> Unit,
    val onSave: (NoteItemUi) -> Unit,
    val onSuggestSummary: (NoteItemUi) -> Unit,
    val onSuggestTags: (NoteItemUi) -> Unit,
)
