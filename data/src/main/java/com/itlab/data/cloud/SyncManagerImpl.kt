package com.itlab.data.cloud

import com.itlab.data.dao.NoteDao
import com.itlab.data.mapper.NoteEntityJsonConverter
import com.itlab.domain.cloud.CloudDataSource
import com.itlab.domain.cloud.Result
import com.itlab.domain.cloud.SyncManager
import com.itlab.domain.cloud.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import timber.log.Timber
import java.io.IOException

class SyncManagerImpl(
    private val noteDao: NoteDao,
    private val cloudDataSource: CloudDataSource,
    private val jsonConverter: NoteEntityJsonConverter,
) : SyncManager {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun sync(userId: String) {
        _syncState.value = SyncState.Syncing

        try {
            pushChanges(userId)
            pullUpdates(userId)

            _syncState.value = SyncState.Success
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            handleError("Network error during sync", e)
            throw e
        } catch (e: SerializationException) {
            handleError("Data parsing error", e)
            throw e
        } catch (e: IllegalStateException) {
            handleError("Invalid state during sync", e)
            throw e
        }
    }

    private fun handleError(
        message: String,
        e: Exception,
    ) {
        Timber.e(e, message)
        _syncState.value = SyncState.Error(e.message ?: "Unknown error")
    }

    override suspend fun pushChanges(userId: String) {
        val unsyncedEntities = noteDao.getUnsyncedNotes()

        for (entity in unsyncedEntities) {
            val json = with(jsonConverter) { entity.toJson() }

            val result = cloudDataSource.uploadNote("users/${entity.userId}/notes/${entity.id}", json)

            when (result) {
                is Result.Success -> {
                    val syncedEntity = entity.copy(isSynced = true)
                    noteDao.update(syncedEntity)
                }
                is Result.Error -> {
                    Timber.e(result.exception, "Couldn't upload the note ${entity.id}")
                    throw result.exception
                }
            }
        }
    }

    override suspend fun pullUpdates(userId: String) {
        val metadataResult = cloudDataSource.listNoteMetadata(userId)

        val remoteMetadata =
            when (metadataResult) {
                is Result.Success -> metadataResult.data
                is Result.Error -> throw metadataResult.exception
            }

        val localNotes = noteDao.getAllNotes().first()
        val localIds = localNotes.map { it.id }

        val toDownload =
            remoteMetadata.filter { cloudMeta ->
                cloudMeta.key !in localIds
            }

        for (meta in toDownload) {
            val downloadResult = cloudDataSource.downloadNote(meta.key)

            when (downloadResult) {
                is Result.Success -> {
                    val entity =
                        jsonConverter.toEntity(
                            jsonString = downloadResult.data,
                            userId = userId,
                        )
                    noteDao.insert(entity)
                }
                is Result.Error -> {
                    Timber.e(downloadResult.exception, "Couldn't download the note ${meta.key}")
                    throw downloadResult.exception
                }
            }
        }
    }
}
