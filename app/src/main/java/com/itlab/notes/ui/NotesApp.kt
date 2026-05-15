package com.itlab.notes.ui

import androidx.compose.runtime.Composable
import com.itlab.notes.ui.editor.EditorScreenActions
import com.itlab.notes.ui.editor.editorScreen
import com.itlab.notes.ui.notes.NotesListActions
import com.itlab.notes.ui.notes.directoriesScreen
import com.itlab.notes.ui.notes.notesListScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun notesApp() {
    val viewModel: NotesViewModel = koinViewModel()
    val state = viewModel.uiState

    when (val screen = state.screen) {
        NotesUiScreen.Directories -> directoriesRoute(state, viewModel)

        is NotesUiScreen.DirectoryNotes -> notesListRoute(screen, state, viewModel)

        is NotesUiScreen.NoteEditor -> editorRoute(screen, state, viewModel)
    }
}

@Composable
private fun directoriesRoute(
    state: NotesUiState,
    viewModel: NotesViewModel,
) {
    directoriesScreen(
        directories = state.directories,
        onCreateDirectory = { name ->
            viewModel.onEvent(NotesUiEvent.CreateDirectory(name))
        },
        onDeleteDirectory = { directory ->
            viewModel.onEvent(NotesUiEvent.DeleteDirectory(directory.id))
        },
        onRenameDirectory = { directory, newName ->
            viewModel.onEvent(NotesUiEvent.RenameDirectory(directory.id, newName))
        },
        onDirectoryClick = { directory ->
            viewModel.onEvent(NotesUiEvent.OpenDirectory(directory))
        },
    )
}

@Composable
private fun notesListRoute(
    screen: NotesUiScreen.DirectoryNotes,
    state: NotesUiState,
    viewModel: NotesViewModel,
) {
    notesListScreen(
        directoryName = screen.directory.name,
        notes = state.notes,
        directories = state.directories.filter { it.id != "all" },
        actions =
            NotesListActions(
                onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectories) },
                onAddNoteClick = { viewModel.onEvent(NotesUiEvent.CreateNote) },
                onNoteDelete = { note -> viewModel.onEvent(NotesUiEvent.DeleteNote(note.id)) },
                onNoteMove = { noteId, directoryId ->
                    viewModel.onEvent(
                        NotesUiEvent.MoveNoteToDirectory(
                            noteId = noteId,
                            targetDirectoryId = directoryId,
                        ),
                    )
                },
                onNoteClick = { note ->
                    viewModel.onEvent(NotesUiEvent.OpenNote(note))
                },
            ),
    )
}

@Composable
private fun editorRoute(
    screen: NotesUiScreen.NoteEditor,
    state: NotesUiState,
    viewModel: NotesViewModel,
) {
    editorScreen(
        directoryName = screen.directory.name,
        note = screen.note,
        aiState = state.aiState,
        actions =
            EditorScreenActions(
                onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectoryNotes) },
                onSave = { updated -> viewModel.onEvent(NotesUiEvent.SaveNote(updated)) },
                onSuggestSummary = { updated -> viewModel.onEvent(NotesUiEvent.SuggestSummary(updated)) },
                onSuggestTags = { updated -> viewModel.onEvent(NotesUiEvent.SuggestTags(updated)) },
            ),
    )
}
