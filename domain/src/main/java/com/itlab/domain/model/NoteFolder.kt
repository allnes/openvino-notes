package com.itlab.domain.model

import kotlin.time.Clock
import kotlin.time.Instant
import java.util.UUID

data class NoteFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap(),
)
