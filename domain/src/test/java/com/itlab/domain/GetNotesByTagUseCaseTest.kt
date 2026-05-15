package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.GetNotesByTagUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class GetNotesByTagUseCaseTest {
    @MockK
    lateinit var repo: NotesRepository

    private lateinit var getNotesByTagUseCase: GetNotesByTagUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        getNotesByTagUseCase = GetNotesByTagUseCase(repo)
    }

    @Test
    fun `invoke should return all notes when tag is blank`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val notes =
                listOf(
                    Note(userId = "u1", title = "N1", tags = setOf("work"), createdAt = now, updatedAt = now),
                    Note(userId = "u1", title = "N2", tags = setOf("home"), createdAt = now, updatedAt = now),
                )
            coEvery { repo.observeNotes() } returns flowOf(notes)

            val result = getNotesByTagUseCase("   ").first()

            assertEquals(2, result.size)
        }

    @Test
    fun `invoke should filter notes by tag case insensitively`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val notes =
                listOf(
                    Note(
                        userId = "u1",
                        title = "Work Note",
                        tags = setOf("Work", "Urgent"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                    Note(userId = "u1", title = "Home Note", tags = setOf("home"), createdAt = now, updatedAt = now),
                )
            coEvery { repo.observeNotes() } returns flowOf(notes)
            val result = getNotesByTagUseCase("WORK").first()

            assertEquals(1, result.size)
            assertEquals("Work Note", result[0].title)
        }

    @Test
    fun `invoke should return empty list when no notes match tag`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val notes =
                listOf(
                    Note(userId = "u1", title = "Note", tags = setOf("study"), createdAt = now, updatedAt = now),
                )
            coEvery { repo.observeNotes() } returns flowOf(notes)

            val result = getNotesByTagUseCase("vacation").first()
            assertEquals(0, result.size)
        }
}
