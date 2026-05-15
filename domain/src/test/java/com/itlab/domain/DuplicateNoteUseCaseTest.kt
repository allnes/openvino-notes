package com.itlab.domain

import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.DataSource
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.DuplicateNoteUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class DuplicateNoteUseCaseTest {
    @MockK
    lateinit var repo: NotesRepository

    private lateinit var duplicateNoteUseCase: DuplicateNoteUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        duplicateNoteUseCase = DuplicateNoteUseCase(repo)
    }

    @Test
    fun `invoke should duplicate note with all types of content items`() =
        runBlocking {
            val noteId = "original_id"
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            val items =
                listOf(
                    ContentItem.Text(text = "Hello"),
                    ContentItem.Image(
                        source = DataSource(remoteUrl = "http://img.png"),
                        mimeType = "image/png",
                    ),
                    ContentItem.File(
                        source = DataSource(localPath = "/cache/f.pdf"),
                        mimeType = "application/pdf",
                        name = "file.pdf",
                    ),
                    ContentItem.Link(url = "http://google.com"),
                )

            val originalNote =
                Note(
                    userId = "user_1",
                    id = noteId,
                    title = "Original",
                    contentItems = items,
                    createdAt = now,
                    updatedAt = now,
                )

            coEvery { repo.getNoteById(noteId) } returns originalNote
            coEvery { repo.createNote(any()) } returns "new_id"

            val result = duplicateNoteUseCase(noteId)

            assertTrue(result.isSuccess)
        }
}
