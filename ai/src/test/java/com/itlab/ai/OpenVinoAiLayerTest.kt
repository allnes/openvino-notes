package com.itlab.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVinoAiLayerTest {
    @Test
    fun normalizeSummary_trimsSpaces() {
        val processor = ResultProcessor()

        val result = processor.normalizeSummary("  short summary  ")

        assertEquals("short summary", result)
    }

    @Test
    fun normalizeTags_splitsByCommaAndNewLine() {
        val processor = ResultProcessor()

        val result = processor.normalizeTags(" Kotlin, AI\nOpenVINO, kotlin,  ")

        assertEquals(setOf("kotlin", "ai", "openvino"), result)
    }

    @Test
    fun normalizeTags_ignoresBlankItems() {
        val processor = ResultProcessor()

        val result = processor.normalizeTags(",  ,\n  tag-one  ,\n")

        assertEquals(setOf("tag-one"), result)
    }

    @Test
    fun summarize_returnsTrimmedSummary() =
        runBlocking {
            val backend = RecordingLlmBackend("  Summary text  ")
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result = service.summarize("Long note")

            assertEquals("Summary text", result)
            assertEquals(OnDeviceLlmConfig.gemma3SmallIt().summaryMaxNewTokens, backend.lastMaxNewTokens)
            assertTrue(backend.lastPrompt.orEmpty().contains("<start_of_turn>user"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Summarize the note"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Long note"))
        }

    @Test
    fun tagTXT_normalizesCaseAndSeparators() =
        runBlocking {
            val backend = RecordingLlmBackend(" Kotlin, Notes\nAI ")
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result = service.tagTXT("OpenVINO note")

            assertEquals(setOf("kotlin", "notes", "ai"), result)
            assertEquals(OnDeviceLlmConfig.gemma3SmallIt().tagsMaxNewTokens, backend.lastMaxNewTokens)
            assertTrue(backend.lastPrompt.orEmpty().contains("Suggest up to"))
            assertTrue(backend.lastPrompt.orEmpty().contains("OpenVINO note"))
        }

    @Test
    fun tagIMGs_returnsEmptySetBecauseVisionIsSeparateFromGemmaLlm() =
        runBlocking {
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = RecordingLlmBackend("unused")),
                    ResultProcessor(),
                )

            val result = service.tagIMGs(listOf("Cat, Pet", "pet, animal", "  CAT"))

            assertEquals(emptySet<String>(), result)
        }

    @Test
    fun gemmaPromptBuilder_trimsLargeInput() {
        val config = OnDeviceLlmConfig.gemma3SmallIt().copy(maxInputChars = 5)
        val builder = GemmaPromptBuilder(config)

        val prompt = builder.summaryPrompt("123456789")

        assertTrue(prompt.contains("12345"))
        assertTrue(!prompt.contains("123456"))
    }

    private class RecordingLlmBackend(
        private val response: String,
    ) : LlmInferenceBackend {
        var lastPrompt: String? = null
            private set
        var lastMaxNewTokens: Int? = null
            private set

        override fun generate(
            prompt: String,
            maxNewTokens: Int,
        ): String {
            lastPrompt = prompt
            lastMaxNewTokens = maxNewTokens
            return response
        }
    }
}
