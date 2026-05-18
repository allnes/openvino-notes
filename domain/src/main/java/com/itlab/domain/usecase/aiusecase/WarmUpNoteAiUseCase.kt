package com.itlab.domain.usecase.aiusecase

import com.itlab.domain.ai.NoteAiService

class WarmUpNoteAiUseCase(
    private val ai: NoteAiService,
) {
    suspend operator fun invoke(): Result<Unit> =
        runCatching {
            ai.warmUp()
        }
}
