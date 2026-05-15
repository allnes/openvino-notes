package com.itlab.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.model.NoteFolder
import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.NoteItemUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotesViewModel(
    private val useCases: NotesUseCases,
) : ViewModel(),
    NotesViewModelContract {
    override var uiState: NotesUiState by mutableStateOf(
        NotesUiState(screen = NotesUiScreen.Directories),
    )
        private set
    private var notesJob: Job? = null
    private var latestFolders: List<NoteFolder> = emptyList()
    private var latestNotes: List<Note> = emptyList()

    init {
        viewModelScope.launch {
            useCases.observeFoldersUseCase().collect { folders ->
                latestFolders = folders
                recomputeDirectories()
            }
        }

        viewModelScope.launch {
            useCases.observeNotesByFolderUseCase(null).collect { notes ->
                latestNotes = notes
                recomputeDirectories()
            }
        }
    }

    override fun onEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.OpenDirectory,
            NotesUiEvent.BackToDirectories,
            is NotesUiEvent.OpenNote,
            NotesUiEvent.CreateNote,
            NotesUiEvent.BackToDirectoryNotes,
            -> handleNavigationEvent(event)

            is NotesUiEvent.CreateDirectory,
            is NotesUiEvent.RenameDirectory,
            is NotesUiEvent.DeleteDirectory,
            -> handleDirectoryEvent(event)

            is NotesUiEvent.MoveNoteToDirectory,
            is NotesUiEvent.DeleteNote,
            -> handleNoteEvent(event)

            is NotesUiEvent.SaveNote,
            is NotesUiEvent.SuggestSummary,
            is NotesUiEvent.SuggestTags,
            -> handleEditorEvent(event)
        }
    }

    private fun handleNavigationEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.OpenDirectory -> openDirectory(event.directory)
            NotesUiEvent.BackToDirectories -> backToDirectories()
            is NotesUiEvent.OpenNote -> openNote(event.note)
            NotesUiEvent.CreateNote -> createNote()
            NotesUiEvent.BackToDirectoryNotes -> backToDirectoryNotes()
            else -> Unit
        }
    }

    private fun handleDirectoryEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.CreateDirectory -> createDirectory(event.name)
            is NotesUiEvent.RenameDirectory -> renameDirectory(event)
            is NotesUiEvent.DeleteDirectory -> deleteDirectory(event.directoryId)
            else -> Unit
        }
    }

    private fun handleNoteEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.MoveNoteToDirectory -> moveNoteToDirectory(event)
            is NotesUiEvent.DeleteNote -> {
                viewModelScope.launch {
                    useCases.deleteNoteUseCase(event.noteId)
                }
            }
            else -> Unit
        }
    }

    private fun handleEditorEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.SaveNote -> saveNote(event.note)
            is NotesUiEvent.SuggestSummary -> suggestAi(event.note, AiSuggestion.Summary)
            is NotesUiEvent.SuggestTags -> suggestAi(event.note, AiSuggestion.Tags)
            else -> Unit
        }
    }

    private val createDirectory: (String) -> Unit = { name ->
        val normalized = name.trim()
        if (normalized.isNotBlank()) {
            viewModelScope.launch {
                useCases.createFolderUseCase(NoteFolder(name = normalized))
            }
        }
    }

    private val renameDirectory: (NotesUiEvent.RenameDirectory) -> Unit = { event ->
        val normalized = event.newName.trim()
        if (normalized.isNotBlank() && event.directoryId != "all") {
            viewModelScope.launch {
                val existingFolder = useCases.getFolderUseCase(event.directoryId) ?: return@launch
                useCases.updateFolderUseCase(existingFolder.copy(name = normalized))
            }
        }
    }

    private val backToDirectories: () -> Unit = {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.Directories,
                notes = emptyList(),
                aiState = AiUiState(),
            )
    }

    private val deleteDirectory: (String) -> Unit = { directoryId ->
        if (directoryId != "all") {
            viewModelScope.launch {
                useCases.deleteFolderUseCase(directoryId)
                if ((uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory?.id == directoryId) {
                    backToDirectories()
                }
            }
        }
    }

    private val moveNoteToDirectory: (NotesUiEvent.MoveNoteToDirectory) -> Unit = { event ->
        if (event.targetDirectoryId != "all") {
            viewModelScope.launch {
                useCases.moveNoteToFolderUseCase(
                    folderId = event.targetDirectoryId,
                    noteId = event.noteId,
                )
            }
        }
    }

    private val openDirectory: (DirectoryItemUi) -> Unit = { directory ->
        uiState =
            uiState.copy(
                screen = NotesUiScreen.DirectoryNotes(directory = directory),
                notes = emptyList(),
                aiState = AiUiState(),
            )
        notesJob?.cancel()
        val isAll = directory.id == "all"
        notesJob =
            viewModelScope.launch {
                val flow =
                    if (isAll) {
                        useCases.observeNotesUseCase()
                    } else {
                        useCases.observeNotesByFolderUseCase(directory.id)
                    }

                flow.collect { notes ->
                    val updatedDirectory = directory.copy(noteCount = notes.size)
                    val currentScreen = uiState.screen
                    uiState =
                        uiState.copy(
                            notes = notes.map { it.toUi() },
                            screen =
                                if (currentScreen is NotesUiScreen.DirectoryNotes &&
                                    currentScreen.directory.id == directory.id
                                ) {
                                    NotesUiScreen.DirectoryNotes(directory = updatedDirectory)
                                } else {
                                    currentScreen
                                },
                        )
                }
            }
    }

    private val openNote: (NoteItemUi) -> Unit = { note ->
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory
        if (dir != null) {
            uiState =
                uiState.copy(
                    screen = NotesUiScreen.NoteEditor(directory = dir, note = note),
                    aiState = AiUiState(),
                )
        }
    }

    private val createNote: () -> Unit = {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory
        if (dir != null) {
            val newNote =
                Note(folderId = dir.id.asDomainFolderId()).toUi()
            uiState =
                uiState.copy(
                    screen = NotesUiScreen.NoteEditor(directory = dir, note = newNote),
                    aiState = AiUiState(),
                )
        }
    }

    private val backToDirectoryNotes: () -> Unit = {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor
        if (editor != null) {
            uiState =
                uiState.copy(
                    screen = NotesUiScreen.DirectoryNotes(directory = editor.directory),
                    aiState = AiUiState(),
                )
        }
    }

    private fun saveNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            upsertEditorNote(note, editor, latestNotes, useCases)
                .onSuccess {
                    uiState =
                        uiState.copy(
                            screen = NotesUiScreen.DirectoryNotes(directory = editor.directory),
                            aiState = AiUiState(),
                        )
                }.onFailure { error ->
                    updateAiState { it.copy(errorMessage = error.userMessage("Unable to save note")) }
                }
        }
    }

    private fun suggestAi(
        note: NoteItemUi,
        suggestion: AiSuggestion,
    ) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            updateAiState { suggestion.startState(it) }
            val savedNote =
                upsertEditorNote(note, editor, latestNotes, useCases)
                    .getOrElse { error ->
                        updateAiState { suggestion.errorState(it, error) }
                        return@launch
                    }
            updateEditorNote(savedNote)

            val generated =
                when (suggestion) {
                    AiSuggestion.Summary ->
                        useCases
                            .suggestSummaryUseCase(savedNote.id)
                            .mapCatching { summary ->
                                useCases.applySummaryUseCase(savedNote.id, summary).getOrThrow()
                                savedNote.copy(summary = summary)
                            }
                    AiSuggestion.Tags ->
                        useCases
                            .suggestTagsUseCase(savedNote.id)
                            .mapCatching { tags ->
                                useCases.applyTagsUseCase(savedNote.id, tags).getOrThrow()
                                savedNote.copy(tags = tags)
                            }
                }

            generated
                .onSuccess { updatedNote ->
                    updateEditorNote(updatedNote)
                    updateAiState { suggestion.successState(it) }
                }.onFailure { error ->
                    updateAiState { suggestion.errorState(it, error) }
                }
        }
    }

    private val updateEditorNote: (NoteItemUi) -> Unit = { note ->
        val editor = uiState.screen as? NotesUiScreen.NoteEditor
        if (editor != null) {
            uiState =
                uiState.copy(
                    screen =
                        NotesUiScreen.NoteEditor(
                            directory = editor.directory,
                            note = note,
                        ),
                )
        }
    }

    private val updateAiState: ((AiUiState) -> AiUiState) -> Unit = { update ->
        uiState = uiState.copy(aiState = update(uiState.aiState))
    }

    private val recomputeDirectories: () -> Unit = {
        val countsByFolderId = latestNotes.groupingBy { it.folderId }.eachCount()
        val allNotesCount = latestNotes.size

        val allNotesDir = DirectoryItemUi(id = "all", name = "All Notes", noteCount = allNotesCount)

        val directories =
            listOf(allNotesDir) +
                latestFolders.map { folder ->
                    val count = countsByFolderId[folder.id] ?: 0
                    folder.toUi(noteCount = count)
                }

        uiState = uiState.copy(directories = directories)

        // If a directory screen is currently open, keep the directory object in sync with the new count.
        val opened = uiState.screen as? NotesUiScreen.DirectoryNotes
        if (opened != null) {
            val updatedDir = directories.firstOrNull { it.id == opened.directory.id }
            if (updatedDir != null && updatedDir.noteCount != opened.directory.noteCount) {
                uiState = uiState.copy(screen = NotesUiScreen.DirectoryNotes(directory = updatedDir))
            }
        }
    }

    override fun onCleared() {
        notesJob?.cancel()
        super.onCleared()
    }
}

private enum class AiSuggestion {
    Summary,
    Tags,
}

private fun AiSuggestion.startState(state: AiUiState): AiUiState =
    when (this) {
        AiSuggestion.Summary -> state.copy(isGeneratingSummary = true, errorMessage = null)
        AiSuggestion.Tags -> state.copy(isGeneratingTags = true, errorMessage = null)
    }

private fun AiSuggestion.successState(state: AiUiState): AiUiState =
    when (this) {
        AiSuggestion.Summary -> state.copy(isGeneratingSummary = false, errorMessage = null)
        AiSuggestion.Tags -> state.copy(isGeneratingTags = false, errorMessage = null)
    }

private fun AiSuggestion.errorState(
    state: AiUiState,
    error: Throwable,
): AiUiState =
    when (this) {
        AiSuggestion.Summary ->
            state.copy(
                isGeneratingSummary = false,
                errorMessage = error.userMessage("Unable to generate summary"),
            )
        AiSuggestion.Tags ->
            state.copy(
                isGeneratingTags = false,
                errorMessage = error.userMessage("Unable to suggest tags"),
            )
    }

private suspend fun upsertEditorNote(
    note: NoteItemUi,
    editor: NotesUiScreen.NoteEditor,
    latestNotes: List<Note>,
    useCases: NotesUseCases,
): Result<NoteItemUi> =
    runCatching {
        val targetFolderId = note.folderId ?: editor.directory.id.asDomainFolderId()
        val existing = latestNotes.firstOrNull { it.id == note.id }
        if (existing != null) {
            useCases.updateNoteUseCase(existing.applyUiUpdate(note, targetFolderId)).getOrThrow()
            note.copy(folderId = targetFolderId)
        } else {
            val savedId = useCases.createNoteUseCase(note.toDomain(folderId = targetFolderId)).getOrThrow()
            note.copy(id = savedId, folderId = targetFolderId)
        }
    }

internal fun NoteFolder.toUi(noteCount: Int): DirectoryItemUi =
    DirectoryItemUi(id = id, name = name, noteCount = noteCount)

internal fun Note.toUi(): NoteItemUi =
    NoteItemUi(
        id = id,
        title = title,
        content =
            contentItems
                .filterIsInstance<ContentItem.Text>()
                .joinToString("\n") { it.text },
        folderId = folderId,
        tags = tags,
        summary = summary,
    )

internal fun NoteItemUi.toDomain(folderId: String?): Note =
    Note(
        id = id,
        title = title,
        folderId = folderId,
        contentItems = listOf(ContentItem.Text(content)),
        tags = tags,
        summary = summary,
    )

internal fun Note.applyUiUpdate(
    ui: NoteItemUi,
    targetFolderId: String?,
): Note {
    val nonTextContent = contentItems.filterNot { it is ContentItem.Text }
    val updatedText =
        ui.content
            .takeIf { it.isNotBlank() }
            ?.let { ContentItem.Text(it) }

    return copy(
        title = ui.title,
        folderId = targetFolderId,
        contentItems = if (updatedText != null) nonTextContent + updatedText else nonTextContent,
        tags = ui.tags,
        summary = ui.summary,
    )
}

internal fun String.asDomainFolderId(): String? = if (this == "all") null else this

private fun Throwable.userMessage(fallback: String): String = message ?: fallback
