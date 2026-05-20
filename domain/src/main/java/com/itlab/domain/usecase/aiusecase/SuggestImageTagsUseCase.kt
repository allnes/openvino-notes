package com.itlab.domain.usecase.aiusecase

import com.itlab.domain.ai.NoteAiService
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository

class SuggestImageTagsUseCase(
    private val ai: NoteAiService,
    private val repo: NotesRepository,
) {
    private fun extractImages(note: Note): List<String> =
        note.contentItems
            .filterIsInstance<ContentItem.Image>()
            .mapNotNull { image ->
                image.source.localPath ?: image.source.remoteUrl
            }

    suspend operator fun invoke(
        noteId: String,
        maxTags: Int = 4,
    ): Result<Set<String>> =
        runCatching {
            val note =
                repo.getNoteById(noteId)
                    ?: throw IllegalArgumentException("Note not found: $noteId")

            val imageTags = ai.tagIMGs(extractImages(note))
            imageTags
                .take(maxTags.coerceAtLeast(0))
                .toSet()
        }
}
