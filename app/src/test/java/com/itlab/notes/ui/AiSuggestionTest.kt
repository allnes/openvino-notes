package com.itlab.notes.ui

import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.NoteItemUi
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSuggestionTest {
    @Test
    fun requireCurrentEditorSnapshot_allowsUnchangedEditorNote() {
        val note = note()
        val state =
            NotesUiState(
                screen = NotesUiScreen.NoteEditor(directory = directory, note = note),
            )

        state.requireCurrentEditorSnapshot(note)
    }

    @Test
    fun requireCurrentEditorSnapshot_rejectsEditedEditorNote() {
        val savedNote = note(content = "original")
        val editedNote = savedNote.copy(content = "user edit while rewrite is running")
        val state =
            NotesUiState(
                screen = NotesUiScreen.NoteEditor(directory = directory, note = editedNote),
            )

        val error = runCatching { state.requireCurrentEditorSnapshot(savedNote) }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    private companion object {
        val directory = DirectoryItemUi(id = "dir", name = "Work", noteCount = 1)

        fun note(content: String = "content") =
            NoteItemUi(
                id = "note",
                userId = "user",
                title = "Title",
                content = content,
                folderId = "dir",
            )
    }
}
