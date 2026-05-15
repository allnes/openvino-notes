package com.itlab.data.cloud

import com.itlab.data.dao.NoteDao
import com.itlab.data.entity.NoteEntity
import com.itlab.data.mapper.NoteEntityJsonConverter
import com.itlab.domain.cloud.CloudDataSource
import com.itlab.domain.cloud.CloudNoteMetadata
import com.itlab.domain.cloud.Result
import com.itlab.domain.cloud.SyncState
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.IOException
import kotlin.time.Clock

class SyncManagerImplTest {
    @MockK
    lateinit var noteDao: NoteDao

    @MockK
    lateinit var cloudDataSource: CloudDataSource

    @MockK
    lateinit var jsonConverter: NoteEntityJsonConverter

    private lateinit var syncManager: SyncManagerImpl
    private val now = Clock.System.now()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Сажаем тестовое дерево, чтобы безопасно поглощать логи Timber
        Timber.plant(
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    // Ничего не делаем
                }
            },
        )

        syncManager = SyncManagerImpl(noteDao, cloudDataSource, jsonConverter)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        unmockkAll()
    }

    @Test(expected = IOException::class)
    fun `sync should handle IOException`() =
        runBlocking {
            coEvery { noteDao.getUnsyncedNotes() } throws IOException("No Internet")

            try {
                syncManager.sync("user1")
            } finally {
                val state = syncManager.syncState.value
                assertTrue(state is SyncState.Error)
                assertEquals("No Internet", (state as SyncState.Error).message)
            }
        }

    @Test(expected = SerializationException::class)
    fun `sync should handle SerializationException`() =
        runBlocking {
            coEvery { noteDao.getUnsyncedNotes() } throws SerializationException("Bad JSON")

            try {
                syncManager.sync("user1")
            } finally {
                assertTrue(syncManager.syncState.value is SyncState.Error)
            }
        }

    @Test(expected = IllegalStateException::class)
    fun `sync should handle IllegalStateException`() =
        runBlocking {
            coEvery { noteDao.getUnsyncedNotes() } throws IllegalStateException("Wrong state")

            try {
                syncManager.sync("user1")
            } finally {
                assertTrue(syncManager.syncState.value is SyncState.Error)
            }
        }

    @Test
    fun `pushChanges should throw and log on Result Error`() =
        runBlocking {
            val note = createTestNote("1")
            val exception = Exception("Upload Failed")

            coEvery { noteDao.getUnsyncedNotes() } returns listOf(note)
            with(jsonConverter) { every { note.toJson() } returns "{}" }
            coEvery { cloudDataSource.uploadNote(any(), any()) } returns Result.Error(exception)

            val result = runCatching { syncManager.pushChanges("user1") }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `pullUpdates should throw and log when downloadNote fails`() =
        runBlocking {
            val meta = CloudNoteMetadata("note1", now)
            val exception = Exception("Download Failed")

            coEvery { cloudDataSource.listNoteMetadata(any()) } returns Result.Success(listOf(meta))
            every { noteDao.getAllNotes() } returns flowOf(emptyList())
            coEvery { cloudDataSource.downloadNote("note1") } returns Result.Error(exception)

            val result = runCatching { syncManager.pullUpdates("user1") }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `pullUpdates should throw when listNoteMetadata fails`() =
        runBlocking {
            val exception = Exception("List Failed")
            coEvery { cloudDataSource.listNoteMetadata("user1") } returns Result.Error(exception)

            val result = runCatching { syncManager.pullUpdates("user1") }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `sync should complete full cycle with push and pull`() =
        runBlocking {
            val userId = "user1"
            val localNoteId = "local_1"
            val remoteNoteId = "remote_1"

            val expectedLocalPath = "users/$userId/notes/$localNoteId"
            val expectedRemotePath = "users/$userId/notes/$remoteNoteId"

            val unsyncedNote = createTestNote(localNoteId).copy(userId = userId, isSynced = false)
            coEvery { noteDao.getUnsyncedNotes() } returns listOf(unsyncedNote)
            with(jsonConverter) {
                every { unsyncedNote.toJson() } returns "{\"id\":\"$localNoteId\"}"
            }
            coEvery { cloudDataSource.uploadNote(expectedLocalPath, any()) } returns Result.Success(Unit)
            coEvery { noteDao.update(any()) } just Runs

            val cloudMeta = CloudNoteMetadata(key = expectedRemotePath, updatedAt = now)
            coEvery { cloudDataSource.listNoteMetadata(userId) } returns Result.Success(listOf(cloudMeta))

            val localNote = createTestNote(localNoteId).copy(userId = userId, isSynced = true)
            every { noteDao.getAllNotes() } returns flowOf(listOf(localNote))

            val remoteJson = "{\"id\":\"$remoteNoteId\"}"
            val remoteEntity = createTestNote(remoteNoteId).copy(userId = userId)

            coEvery { cloudDataSource.downloadNote(expectedRemotePath) } returns Result.Success(remoteJson)
            every { jsonConverter.toEntity(remoteJson, userId) } returns remoteEntity
            coEvery { noteDao.insert(remoteEntity) } just Runs

            syncManager.sync(userId)

            assertEquals(SyncState.Success, syncManager.syncState.value)

            coVerifyOrder {
                noteDao.getUnsyncedNotes()
                cloudDataSource.uploadNote(expectedLocalPath, any())
                noteDao.update(match { it.id == localNoteId && it.isSynced })

                cloudDataSource.listNoteMetadata(userId)
                cloudDataSource.downloadNote(expectedRemotePath)
                noteDao.insert(match { it.id == remoteNoteId })
            }
        }

    private fun createTestNote(id: String) =
        NoteEntity(
            id = id,
            title = "Title",
            content = "Content",
            userId = "user1",
            isSynced = false,
            createdAt = now,
            updatedAt = now,
        )
}
