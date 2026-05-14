package com.itlab.notes.ui

import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.NoteItemUi

/**
 * UI contract for the Notes feature.
 * Keeps state & events in one place so screens stay "dumb" (render-only).
 */
sealed interface NotesUiScreen {
    data object Directories : NotesUiScreen

    data class DirectoryNotes(
        val directory: DirectoryItemUi,
    ) : NotesUiScreen

    data class NoteEditor(
        val directory: DirectoryItemUi,
        val note: NoteItemUi,
    ) : NotesUiScreen
}

data class NotesUiState(
    val screen: NotesUiScreen = NotesUiScreen.Directories,
    val directories: List<DirectoryItemUi> = emptyList(),
    val notes: List<NoteItemUi> = emptyList(),
    val aiState: AiUiState = AiUiState(),
)

data class AiUiState(
    val isGeneratingSummary: Boolean = false,
    val isGeneratingTags: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface NotesUiEvent {
    data class OpenDirectory(
        val directory: DirectoryItemUi,
    ) : NotesUiEvent

    data object BackToDirectories : NotesUiEvent

    data class OpenNote(
        val note: NoteItemUi,
    ) : NotesUiEvent

    data object CreateNote : NotesUiEvent

    data class CreateDirectory(
        val name: String,
    ) : NotesUiEvent

    data class DeleteDirectory(
        val directoryId: String,
    ) : NotesUiEvent

    data object BackToDirectoryNotes : NotesUiEvent

    data class SaveNote(
        val note: NoteItemUi,
    ) : NotesUiEvent

    data class SuggestSummary(
        val note: NoteItemUi,
    ) : NotesUiEvent

    data class SuggestTags(
        val note: NoteItemUi,
    ) : NotesUiEvent

    data class DeleteNote(
        val noteId: String,
    ) : NotesUiEvent

    data class RenameDirectory(
        val directoryId: String,
        val newName: String,
    ) : NotesUiEvent

    data class MoveNoteToDirectory(
        val noteId: String,
        val targetDirectoryId: String,
    ) : NotesUiEvent
}

interface NotesViewModelContract {
    val uiState: NotesUiState

    fun onEvent(event: NotesUiEvent)
}
