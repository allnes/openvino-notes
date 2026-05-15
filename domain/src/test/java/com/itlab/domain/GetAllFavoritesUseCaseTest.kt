package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.GetAllFavoritesUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class GetAllFavoritesUseCaseTest {
    @MockK
    lateinit var repo: NotesRepository

    private lateinit var getAllFavoritesUseCase: GetAllFavoritesUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        getAllFavoritesUseCase = GetAllFavoritesUseCase(repo)
    }

    @Test
    fun `invoke should filter only favorite notes`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val note1 =
                Note(userId = "u1", id = "1", title = "Fav", isFavorite = true, createdAt = now, updatedAt = now)
            val note2 =
                Note(userId = "u1", id = "2", title = "Not Fav", isFavorite = false, createdAt = now, updatedAt = now)

            coEvery { repo.observeNotes() } returns flowOf(listOf(note1, note2))

            val result = getAllFavoritesUseCase().first()

            assertEquals(1, result.size)
            assertTrue(result.all { it.isFavorite })
            assertEquals("1", result[0].id)
        }

    @Test
    fun `invoke should return empty list when no favorites exist`() =
        runBlocking {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val note = Note(userId = "u1", id = "3", isFavorite = false, createdAt = now, updatedAt = now)

            coEvery { repo.observeNotes() } returns flowOf(listOf(note))

            val result = getAllFavoritesUseCase().first()

            assertTrue(result.isEmpty())
        }
}
