package com.itlab.domain.usecase.aiusecase

import com.itlab.domain.ai.NoteAiService
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository

class SuggestTagsUseCase(
    private val ai: NoteAiService,
    private val repo: NotesRepository,
) {
    private fun extractText(note: Note): String =
        note.contentItems
            .filterIsInstance<ContentItem.Text>()
            .joinToString("\n") { it.text }

    private fun extractImages(note: Note): List<String> =
        note.contentItems
            .filterIsInstance<ContentItem.Image>()
            .mapNotNull { image ->
                image.source.localPath ?: image.source.remoteUrl
            }

    suspend operator fun invoke(
        noteId: String,
        maxInputTokens: Int = 384,
        maxTags: Int = 4,
    ): Result<Set<String>> =
        runCatching {
            val note =
                repo.getNoteById(noteId)
                    ?: throw IllegalArgumentException("Note not found: $noteId")

            val text = extractText(note)
            val imageUrls = extractImages(note)
            val textTags =
                ai.suggestTags(
                    text = text,
                    maxInputTokens = maxInputTokens,
                    maxTags = maxTags,
                )
            val imageTags = ai.tagIMGs(imageUrls)

            (textTags + imageTags).take(maxTags).toSet()
        }
}
