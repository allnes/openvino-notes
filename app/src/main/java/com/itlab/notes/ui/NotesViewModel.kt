package com.itlab.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.model.NoteFolder
import com.itlab.notes.media.withoutTextItems
import com.itlab.notes.ui.notes.ALL_DIRECTORY_ID
import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.FAVORITES_DIRECTORY_ID
import com.itlab.notes.ui.notes.NoteItemUi
import com.itlab.notes.ui.notes.RECENT_DIRECTORY_ID
import com.itlab.notes.ui.notes.canCreateNotesInDirectory
import com.itlab.notes.ui.notes.coerceDirectoryNameLength
import com.itlab.notes.ui.notes.isVirtualDirectory
import com.itlab.notes.ui.toSingleLineText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private var aiJob: Job? = null
    private var aiWarmUpJob: Job? = null
    private var aiWarmUpStarted = false
    private var aiReady = false
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
            is NotesUiEvent.OpenDirectory -> {
                releaseAiResourcesIfEditorOpen()
                openDirectory(event.directory)
            }
            NotesUiEvent.BackToDirectories -> {
                releaseAiResources()
                backToDirectories()
            }
            is NotesUiEvent.OpenNote -> {
                cancelAiGeneration()
                openNote(event.note)
            }
            NotesUiEvent.CreateNote -> {
                cancelAiGeneration()
                createNote()
            }
            is NotesUiEvent.CreateDirectory -> {
                val normalized =
                    event.name
                        .toSingleLineText()
                        .trim()
                        .coerceDirectoryNameLength()
                if (normalized.isNotBlank()) {
                    viewModelScope.launch {
                        useCases.createFolderUseCase(NoteFolder(name = normalized))
                    }
                }
            }
            is NotesUiEvent.RenameDirectory -> renameDirectory(event)
            is NotesUiEvent.DeleteDirectory -> deleteDirectory(event.directoryId)
            is NotesUiEvent.MoveNoteToDirectory -> {
                if (isVirtualDirectory(event.targetDirectoryId)) return
                viewModelScope.launch {
                    useCases.moveNoteToFolderUseCase(
                        folderId = event.targetDirectoryId,
                        noteId = event.noteId,
                    )
                }
            }
            is NotesUiEvent.ToggleNoteFavorite -> toggleNoteFavorite(event.noteId)
            NotesUiEvent.BackToDirectoryNotes -> {
                releaseAiResources()
                backToDirectoryNotes()
            }
            is NotesUiEvent.LeaveEditor -> {
                releaseAiResources()
                leaveEditor(event.note)
            }
            is NotesUiEvent.SaveNote -> {
                releaseAiResources()
                saveNote(event.note)
            }
            is NotesUiEvent.PersistNote -> persistNote(event.note)
            is NotesUiEvent.SuggestSummary -> suggestAi(event.note, AiSuggestion.Summary)
            is NotesUiEvent.SuggestTags -> suggestAi(event.note, AiSuggestion.Tags)
            is NotesUiEvent.RewriteNote -> suggestAi(event.note, AiSuggestion.Rewrite)
            NotesUiEvent.CancelAiGeneration -> cancelAiGeneration()
            is NotesUiEvent.DeleteNote -> {
                viewModelScope.launch {
                    useCases.deleteNoteUseCase(event.noteId)
                }
            }
            is NotesUiEvent.NotesSearchQueryChanged -> onNotesSearchQueryChanged(event.query)
            is NotesUiEvent.DirectorySearchQueryChanged -> {
                uiState = uiState.copy(directorySearchQuery = event.query)
            }
        }
    }

    private fun onNotesSearchQueryChanged(query: String) {
        val directory = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory ?: return
        uiState = uiState.copy(notesSearchQuery = query)
        startNotesCollection(directory, query)
    }

    private fun renameDirectory(event: NotesUiEvent.RenameDirectory) {
        val normalized =
            event.newName
                .toSingleLineText()
                .trim()
                .coerceDirectoryNameLength()
        if (normalized.isBlank() || isVirtualDirectory(event.directoryId)) return
        viewModelScope.launch {
            val existingFolder = useCases.getFolderUseCase(event.directoryId) ?: return@launch
            useCases.updateFolderUseCase(existingFolder.copy(name = normalized))
        }
    }

    private fun deleteDirectory(directoryId: String) {
        if (isVirtualDirectory(directoryId)) return
        viewModelScope.launch {
            useCases.deleteFolderUseCase(directoryId)
            if ((uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory?.id == directoryId) {
                backToDirectories()
            }
        }
    }

    private fun openDirectory(directory: DirectoryItemUi) {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.DirectoryNotes(directory = directory),
                notes = emptyList(),
                notesSearchQuery = "",
                aiState = freshAiState(),
            )
        startNotesCollection(directory, searchQuery = "")
    }

    private fun startNotesCollection(
        directory: DirectoryItemUi,
        searchQuery: String,
    ) {
        notesJob?.cancel()
        notesJob =
            viewModelScope.launch {
                notesFlow(directory, searchQuery).collect { notes ->
                    val opened = uiState.screen as? NotesUiScreen.DirectoryNotes ?: return@collect
                    uiState =
                        uiState.copy(
                            notes = notes.map { it.toUi() },
                            notesSearchQuery = searchQuery,
                            screen =
                                NotesUiScreen.DirectoryNotes(
                                    directory = opened.directory.copy(noteCount = notes.size),
                                ),
                        )
                }
            }
    }

    private fun notesFlow(
        directory: DirectoryItemUi,
        searchQuery: String,
    ): Flow<List<Note>> {
        val normalizedQuery = searchQuery.trim()
        return if (normalizedQuery.isBlank()) {
            when (directory.id) {
                ALL_DIRECTORY_ID -> useCases.observeNotesUseCase()
                FAVORITES_DIRECTORY_ID -> useCases.getAllFavoritesUseCase()
                RECENT_DIRECTORY_ID ->
                    useCases.observeNotesUseCase().map { notes ->
                        notes.sortedByDescending { it.updatedAt }
                    }
                else -> useCases.observeNotesByFolderUseCase(directory.id)
            }
        } else {
            val searchFlow =
                useCases.searchNotesUseCase(
                    query = normalizedQuery,
                    folderId = directory.folderIdForSearch(),
                )
            when (directory.id) {
                FAVORITES_DIRECTORY_ID ->
                    searchFlow.map { notes -> notes.filter { it.isFavorite } }
                RECENT_DIRECTORY_ID ->
                    searchFlow.map { notes ->
                        notes.sortedByDescending { it.updatedAt }
                    }
                else -> searchFlow
            }
        }
    }

    private val backToDirectories: () -> Unit = {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.Directories,
                notes = emptyList(),
                notesSearchQuery = "",
                aiState = freshAiState(),
            )
    }

    private fun openNote(note: NoteItemUi) {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory ?: return
        notesJob?.cancel()
        uiState =
            uiState.copy(
                screen = NotesUiScreen.NoteEditor(directory = dir, note = note),
                aiState = freshAiState(),
            )
        warmUpAi()
    }

    private fun createNote() {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory ?: return
        if (!canCreateNotesInDirectory(dir.id)) return
        notesJob?.cancel()
        val userId = useCases.getUserIdUseCase() ?: "local_user"
        val newNote = Note(userId = userId, folderId = dir.id.asDomainFolderId()).toUi()
        uiState =
            uiState.copy(
                screen = NotesUiScreen.NoteEditor(directory = dir, note = newNote),
                aiState = freshAiState(),
            )
        warmUpAi()
    }

    private fun backToDirectoryNotes() {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        val directory = editor.directory
        uiState =
            uiState.copy(
                screen = NotesUiScreen.DirectoryNotes(directory = directory),
                aiState = freshAiState(),
            )
        startNotesCollection(directory, uiState.notesSearchQuery)
    }

    private fun toggleNoteFavorite(noteId: String) {
        viewModelScope.launch {
            useCases.switchFavoriteUseCase(noteId)
            val editor = uiState.screen as? NotesUiScreen.NoteEditor
            if (editor?.note?.id == noteId) {
                uiState =
                    uiState.copy(
                        screen =
                            editor.copy(
                                note = editor.note.copy(isFavorite = !editor.note.isFavorite),
                            ),
                    )
            }
        }
    }

    private fun persistNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            persistNoteToRepository(note, editor.directory)
        }
    }

    private fun leaveEditor(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            if (note.title.trim().isNotEmpty()) {
                persistNoteToRepository(note, editor.directory)
            }
            navigateBackToDirectoryNotes(editor.directory)
        }
    }

    private fun saveNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            if (!persistNoteToRepository(note, editor.directory)) return@launch
            navigateBackToDirectoryNotes(editor.directory)
        }
    }

    private fun navigateBackToDirectoryNotes(directory: DirectoryItemUi) {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.DirectoryNotes(directory = directory),
                aiState = freshAiState(),
            )
        startNotesCollection(directory, uiState.notesSearchQuery)
    }

    private suspend fun persistNoteToRepository(
        note: NoteItemUi,
        directory: DirectoryItemUi,
    ): Boolean {
        if (note.title.trim().isEmpty()) return false
        val existing = useCases.getNoteUseCase(note.id)
        if (existing == null && !canCreateNotesInDirectory(directory.id)) return false
        val savedNote =
            upsertEditorNote(note, directory)
                .getOrElse { return false }
        val editor = uiState.screen as? NotesUiScreen.NoteEditor
        if (editor?.note?.id == note.id) {
            uiState = uiState.copy(screen = editor.copy(note = savedNote))
        }
        return true
    }

    private suspend fun upsertEditorNote(
        note: NoteItemUi,
        directory: DirectoryItemUi,
    ): Result<NoteItemUi> =
        runCatching {
            require(note.title.trim().isNotEmpty()) { "Title is required" }
            val targetFolderId = note.folderId ?: directory.id.asDomainFolderId()
            val existing = useCases.getNoteUseCase(note.id)
            if (existing != null) {
                useCases.updateNoteUseCase(existing.applyUiUpdate(note, targetFolderId)).getOrThrow()
                note.copy(folderId = targetFolderId)
            } else {
                val savedId = useCases.createNoteUseCase(note.toDomain(folderId = targetFolderId)).getOrThrow()
                note.copy(id = savedId, folderId = targetFolderId)
            }
        }

    private fun suggestAi(
        note: NoteItemUi,
        suggestion: AiSuggestion,
    ) {
        if (aiJob?.isActive == true) return
        if (!uiState.aiState.canGenerate) {
            warmUpAi()
            return
        }

        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        val job =
            viewModelScope.launch {
                updateAiState { suggestion.startState(it) }
                val savedNote =
                    upsertEditorNote(note, editor.directory)
                        .getOrElse { error ->
                            updateAiState { suggestion.errorState(it, error) }
                            return@launch
                        }
                updateEditorNote(savedNote)

                val generated =
                    generateAiSuggestion(
                        suggestion = suggestion,
                        savedNote = savedNote,
                        useCases = useCases,
                        currentEditorNote = { uiState.requireCurrentEditorNote(savedNote.id) },
                        ensureCurrentEditorSnapshot = { uiState.requireCurrentEditorSnapshot(savedNote) },
                    )

                generated
                    .onSuccess { updatedNote ->
                        if (uiState.isCurrentEditorNote(savedNote.id)) {
                            updateEditorNote(updatedNote)
                            updateAiState { suggestion.successState(it) }
                        }
                    }.onFailure { error ->
                        if (error !is CancellationException && uiState.isCurrentEditorNote(savedNote.id)) {
                            updateAiState { suggestion.errorState(it, error) }
                        }
                    }
            }
        aiJob = job
        job.invokeOnCompletion {
            if (aiJob === job) {
                aiJob = null
            }
        }
    }

    private fun updateEditorNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        uiState =
            uiState.copy(
                screen =
                    NotesUiScreen.NoteEditor(
                        directory = editor.directory,
                        note = note,
                    ),
            )
    }

    private fun updateAiState(update: (AiUiState) -> AiUiState) {
        uiState = uiState.copy(aiState = update(uiState.aiState))
    }

    private fun freshAiState(): AiUiState =
        AiUiState(
            isWarmingUp = aiWarmUpJob?.isActive == true,
            isReady = aiReady,
        )

    private fun cancelAiGeneration() {
        aiJob?.cancel()
        aiJob = null
        updateAiState { freshAiState() }
    }

    private fun releaseAiResourcesIfEditorOpen() {
        if (uiState.screen is NotesUiScreen.NoteEditor) {
            releaseAiResources()
        } else {
            cancelAiGeneration()
        }
    }

    private fun releaseAiResources() {
        aiJob?.cancel()
        aiJob = null
        aiWarmUpJob?.cancel()
        aiWarmUpJob = null
        aiWarmUpStarted = false
        aiReady = false
        updateAiState { AiUiState() }
        viewModelScope.launch(Dispatchers.Default) {
            useCases.releaseNoteAiUseCase()
        }
    }

    private fun warmUpAi() {
        if (aiReady) {
            updateAiState { it.copy(isWarmingUp = false, isReady = true, errorMessage = null) }
            return
        }
        if (aiWarmUpStarted || aiWarmUpJob?.isActive == true) {
            updateAiState { it.copy(isWarmingUp = true, isReady = false, errorMessage = null) }
            return
        }

        aiWarmUpStarted = true
        updateAiState { it.copy(isWarmingUp = true, isReady = false, errorMessage = null) }
        aiWarmUpJob =
            viewModelScope.launch {
                val result = useCases.warmUpNoteAiUseCase()
                if (result.isSuccess) {
                    aiReady = true
                    updateAiState { it.copy(isWarmingUp = false, isReady = true, errorMessage = null) }
                } else {
                    aiReady = false
                    aiWarmUpStarted = false
                    updateAiState {
                        it.copy(
                            isWarmingUp = false,
                            isReady = false,
                            errorMessage =
                                result
                                    .exceptionOrNull()
                                    ?.userMessage("Unable to prepare AI model")
                                    ?: "Unable to prepare AI model",
                        )
                    }
                }
            }
    }

    private fun recomputeDirectories() {
        val countsByFolderId = latestNotes.groupingBy { it.folderId }.eachCount()
        val allNotesCount = latestNotes.size

        val favoritesCount = latestNotes.count { it.isFavorite }
        val allNotesDir = DirectoryItemUi(id = ALL_DIRECTORY_ID, name = "All Notes", noteCount = allNotesCount)
        val favoritesDir =
            DirectoryItemUi(
                id = FAVORITES_DIRECTORY_ID,
                name = "Favorites",
                noteCount = favoritesCount,
            )

        val directories =
            listOf(allNotesDir, favoritesDir) +
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
        aiJob?.cancel()
        aiWarmUpJob?.cancel()
        useCases.releaseNoteAiUseCase()
        super.onCleared()
    }
}

internal fun NoteFolder.toUi(noteCount: Int): DirectoryItemUi =
    DirectoryItemUi(id = id, name = name, noteCount = noteCount)

internal fun Note.toUi(): NoteItemUi =
    NoteItemUi(
        id = id,
        userId = userId,
        title = title,
        content =
            contentItems
                .filterIsInstance<ContentItem.Text>()
                .joinToString("\n") { it.text },
        folderId = folderId,
        attachments = contentItems.withoutTextItems(),
        isFavorite = isFavorite,
        tags = tags,
        summary = summary,
    )

internal fun NoteItemUi.toContentItems(): List<ContentItem> =
    buildList {
        if (content.isNotBlank()) add(ContentItem.Text(text = content))
        addAll(attachments.withoutTextItems())
    }

internal fun NoteItemUi.toDomain(folderId: String?): Note =
    Note(
        userId = userId,
        id = id,
        title = title,
        folderId = folderId,
        contentItems = toContentItems(),
        isFavorite = isFavorite,
        tags = tags,
        summary = summary,
    )

internal fun Note.applyUiUpdate(
    ui: NoteItemUi,
    targetFolderId: String?,
): Note =
    copy(
        title = ui.title,
        folderId = targetFolderId,
        contentItems = ui.toContentItems(),
        isFavorite = ui.isFavorite,
        tags = ui.tags,
        summary = ui.summary,
    )

internal fun String.asDomainFolderId(): String? =
    when (this) {
        ALL_DIRECTORY_ID,
        RECENT_DIRECTORY_ID,
        FAVORITES_DIRECTORY_ID,
        -> null
        else -> this
    }

internal fun DirectoryItemUi.folderIdForSearch(): String? =
    when (id) {
        ALL_DIRECTORY_ID,
        RECENT_DIRECTORY_ID,
        FAVORITES_DIRECTORY_ID,
        -> null
        else -> id
    }

internal fun filterDirectoriesByName(
    directories: List<DirectoryItemUi>,
    query: String,
): List<DirectoryItemUi> {
    val normalized = query.trim()
    if (normalized.isBlank()) return directories
    return directories.filter { directory ->
        directory.name.contains(normalized, ignoreCase = true)
    }
}
