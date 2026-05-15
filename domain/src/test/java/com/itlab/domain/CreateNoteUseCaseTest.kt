package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.model.SyncState
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.CreateNoteUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

class CreateNoteUseCaseTest {
    @MockK
    lateinit var repo: NotesRepository

    private lateinit var createNoteUseCase: CreateNoteUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        createNoteUseCase = CreateNoteUseCase(repo)
    }

    @Test
    fun `invoke should return failure when title duplicate exists in same folder`() =
        runBlocking {
            val folderId = "folder_1"
            val existingNote =
                mockk<Note> {
                    every { this@mockk.folderId } returns folderId
                    every { this@mockk.title } returns "  Original Title  "
                }
            coEvery { repo.observeNotes() } returns flowOf(listOf(existingNote))

            val newNote =
                mockk<Note> {
                    every { this@mockk.folderId } returns folderId
                    every { this@mockk.title } returns "original title" // Другой регистр
                }

            val result = createNoteUseCase(newNote)

            assertTrue(result.isFailure)
            assertEquals(
                "Note with title 'original title' already exists in this folder",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    fun `invoke should return success when same title is in different folder`() =
        runBlocking {
            val existingNote =
                Note(
                    id = "1",
                    title = "My Note",
                    folderId = "FOLDER_A",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    userId = "",
                    contentItems = emptyList(),
                    tags = emptySet(),
                    isFavorite = false,
                    syncStatus = SyncState.PENDING,
                )

            coEvery { repo.observeNotes() } returns flowOf(listOf(existingNote))
            coEvery { repo.createNote(any()) } returns "new_id"

            val newNote =
                existingNote.copy(
                    id = "",
                    folderId = "FOLDER_B",
                )

            val result = createNoteUseCase(newNote)

            assertTrue("Ожидался успех, но пришла ошибка: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals("new_id", result.getOrNull())
        }
}
