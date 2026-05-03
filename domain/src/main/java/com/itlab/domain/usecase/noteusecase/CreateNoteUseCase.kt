package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock
import java.util.UUID

class CreateNoteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(note: Note): String {
        val now = Clock.System.now()

        val note =
            note.copy(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                updatedAt = now,
            )
        return repo.createNote(note)
    }
}
