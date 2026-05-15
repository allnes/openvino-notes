package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.MoveNoteToFolderUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class MoveNoteToFolderUseCaseTest {
    @MockK
    lateinit var notesRepo: NotesRepository

    @MockK
    lateinit var folderRepo: NoteFolderRepository

    private lateinit var moveNoteToFolderUseCase: MoveNoteToFolderUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        moveNoteToFolderUseCase = MoveNoteToFolderUseCase(notesRepo, folderRepo)
    }

    @Test
    fun `invoke should update note folder when both exist`() =
        runBlocking {
            val folderId = "folder_1"
            val noteId = "note_1"
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            val note = Note(userId = "u1", id = noteId, folderId = "old_folder", createdAt = now, updatedAt = now)

            coEvery { folderRepo.getFolderById(folderId) } returns mockk() // Папка найдена
            coEvery { notesRepo.getNoteById(noteId) } returns note
            coEvery { notesRepo.updateNote(any()) } returns Unit

            val result = moveNoteToFolderUseCase(folderId, noteId)

            assertTrue(result.isSuccess)
            coVerify { notesRepo.updateNote(match { it.folderId == folderId }) }
        }

    @Test
    fun `invoke should return failure when folder not found`() =
        runBlocking {
            val folderId = "missing_folder"
            coEvery { folderRepo.getFolderById(folderId) } returns null // Покрываем "Folder not found"

            val result = moveNoteToFolderUseCase(folderId, "some_note")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Folder not found") == true)
        }

    @Test
    fun `invoke should return failure when note not found`() =
        runBlocking {
            val folderId = "folder_1"
            val noteId = "missing_note"
            coEvery { folderRepo.getFolderById(folderId) } returns mockk()
            coEvery { notesRepo.getNoteById(noteId) } returns null // Покрываем "Note not found"

            val result = moveNoteToFolderUseCase(folderId, noteId)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
}
