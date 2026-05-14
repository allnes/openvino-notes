package com.itlab.ai

import com.itlab.domain.ai.NoteAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenVinoNoteAiService(
    private val engine: OpenVinoEngine,
    private val processor: ResultProcessor,
) : NoteAiService {
    override suspend fun summarize(text: String): String =
        withContext(Dispatchers.Default) {
            val llmResult = engine.runLlmSummary(text)
            processor.normalizeSummary(llmResult)
        }

    override suspend fun tagTXT(text: String): Set<String> =
        withContext(Dispatchers.Default) {
            val llmResult = engine.runLlmTagging(text)
            processor.normalizeTags(llmResult)
        }

    override suspend fun tagIMGs(img: List<String>): Set<String> {
        // This is a text LLM path. Image tagging stays in a separate AI direction.
        return emptySet()
    }
}
