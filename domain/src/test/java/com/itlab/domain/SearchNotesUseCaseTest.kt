package com.itlab.domain

import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.SearchNotesUseCase
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

class SearchNotesUseCaseTest {
    @MockK
    lateinit var repo: NotesRepository

    private lateinit var searchNotesUseCase: SearchNotesUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        searchNotesUseCase = SearchNotesUseCase(repo)
    }

    @Test
    fun `invoke should return all notes when query is blank`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val notes = listOf(Note(userId = "u1", title = "Note", createdAt = now, updatedAt = now))
            coEvery { repo.observeNotes() } returns flowOf(notes)

            val result = searchNotesUseCase("   ").first()

            assertEquals(1, result.size)
        }

    @Test
    fun `invoke should match query in title or content text`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val notes =
                listOf(
                    Note(userId = "u1", id = "1", title = "Shopping List", createdAt = now, updatedAt = now),
                    Note(
                        userId = "u1",
                        id = "2",
                        title = "Ideas",
                        contentItems = listOf(ContentItem.Text(text = "buy some milk")),
                        createdAt = now,
                        updatedAt = now,
                    ),
                    Note(userId = "u1", id = "3", title = "Work", createdAt = now, updatedAt = now),
                )
            coEvery { repo.observeNotes() } returns flowOf(notes)

            val resultText = searchNotesUseCase("BUY").first()
            assertEquals(1, resultText.size)
            assertEquals("2", resultText[0].id)

            val resultTitle = searchNotesUseCase("shop").first()
            assertEquals(1, resultTitle.size)
            assertEquals("1", resultTitle[0].id)
        }

    @Test
    fun `invoke should return empty list when nothing matches`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val notes = listOf(Note(userId = "u1", title = "A", createdAt = now, updatedAt = now))
            coEvery { repo.observeNotes() } returns flowOf(notes)

            val result = searchNotesUseCase("xyz").first()

            assertEquals(0, result.size)
        }

    @Test
    fun `invoke should cover all branches of content matching`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            val noteWithMixedContent =
                Note(
                    userId = "u1",
                    id = "mixed_id",
                    title = "Title",
                    contentItems =
                        listOf(
                            ContentItem.Link(url = "http://test.com"),
                            ContentItem.Text(text = "random stuff"),
                            ContentItem.Text(text = "target secret message"),
                        ),
                    createdAt = now,
                    updatedAt = now,
                )

            coEvery { repo.observeNotes() } returns flowOf(listOf(noteWithMixedContent))

            val result = searchNotesUseCase("SECRET").first()

            assertEquals(1, result.size)
            assertEquals("mixed_id", result[0].id)
        }
}
