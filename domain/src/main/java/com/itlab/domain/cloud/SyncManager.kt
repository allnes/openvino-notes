package com.itlab.domain.cloud

import kotlinx.coroutines.flow.StateFlow

interface SyncManager {
    val syncState: StateFlow<SyncState>

    suspend fun sync(userId: String)

    suspend fun pushChanges(userId: String)

    suspend fun pullUpdates(userId: String)
}

sealed class SyncState {
    object Idle : SyncState()

    object Syncing : SyncState()

    data class Error(
        val message: String,
    ) : SyncState()

    object Success : SyncState()
}
