package com.itlab.domain.usecase.aiusecase

import com.itlab.domain.ai.NoteAiService
import com.itlab.domain.ai.RewriteStyle
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository

class RewriteNoteUseCase(
    private val ai: NoteAiService,
    private val repo: NotesRepository,
) {
    private fun extractText(note: Note): String =
        note.contentItems
            .filterIsInstance<ContentItem.Text>()
            .joinToString("\n") { it.text }

    suspend operator fun invoke(
        noteId: String,
        style: RewriteStyle = RewriteStyle.CLEANUP,
        maxInputTokens: Int = 768,
        maxNewTokens: Int = 128,
    ): Result<String> =
        runCatching {
            val note =
                repo.getNoteById(noteId)
                    ?: throw IllegalArgumentException("Note not found: $noteId")

            ai.rewrite(
                text = extractText(note),
                style = style,
                maxInputTokens = maxInputTokens,
                maxNewTokens = maxNewTokens,
            )
        }
}
