package com.itlab.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertEquals(OnDeviceLlmConfig.defaultAndroid().summaryMaxNewTokens, backend.lastMaxNewTokens)
            assertTrue(backend.lastPrompt.orEmpty().contains("<|im_start|>user"))
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
            assertEquals(OnDeviceLlmConfig.defaultAndroid().tagsMaxNewTokens, backend.lastMaxNewTokens)
            assertTrue(backend.lastPrompt.orEmpty().contains("Suggest up to"))
            assertTrue(backend.lastPrompt.orEmpty().contains("OpenVINO note"))
        }

    @Test
    fun tagIMGs_returnsEmptySetBecauseVisionIsSeparateFromTextLlm() =
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
    fun noteLlmPromptBuilder_trimsLargeInput() {
        val config = OnDeviceLlmConfig.defaultAndroid().copy(maxInputChars = 5)
        val builder = NoteLlmPromptBuilder(config)

        val prompt = builder.summaryPrompt("123456789")

        assertTrue(prompt.contains("12345"))
        assertTrue(!prompt.contains("123456"))
    }

    @Test
    fun openVinoEngine_returnsEmptyResultForBlankInputWithoutCallingBackend() {
        val backend = RecordingLlmBackend("unused")
        val engine = OpenVinoEngine(llmBackend = backend)

        assertEquals("", engine.runLlmSummary("   "))
        assertEquals("", engine.runLlmTagging("\n\t"))
        assertEquals(null, backend.lastPrompt)
    }

    @Test
    fun unavailableBackend_failsWithConfiguredReason() {
        val backend = UnavailableLlmBackend("missing runtime")

        val failure =
            runCatching {
                backend.generate("prompt", maxNewTokens = 1)
            }.exceptionOrNull()

        assertTrue(failure is MissingLlmRuntimeException)
        assertEquals("missing runtime", failure?.message)
    }

    @Test
    fun missingRuntimeException_preservesCause() {
        val cause = IllegalArgumentException("native loader")

        val failure = MissingLlmRuntimeException("runtime failed", cause)

        assertEquals("runtime failed", failure.message)
        assertEquals(cause, failure.cause)
    }

    @Test
    fun preparePrompt_appendsNoThinkHintWhenReasoningOutputIsDisabled() {
        val config =
            OnDeviceLlmConfig.defaultAndroid().copy(
                includeReasoningOutput = false,
                disableReasoningPromptHint = "/no_think",
            )

        val prompt = preparePrompt("Say ok   ", config)

        assertEquals("Say ok\n/no_think", prompt)
    }

    @Test
    fun preparePrompt_keepsPromptWhenHintAlreadyExists() {
        val config = OnDeviceLlmConfig.defaultAndroid()

        val prompt = preparePrompt("Say ok\n/NO_THINK", config)

        assertEquals("Say ok\n/NO_THINK", prompt)
    }

    @Test
    fun preparePrompt_keepsPromptWhenReasoningOutputIsEnabled() {
        val config =
            OnDeviceLlmConfig.defaultAndroid().copy(
                includeReasoningOutput = true,
                disableReasoningPromptHint = "/no_think",
            )

        val prompt = preparePrompt("Say ok", config)

        assertEquals("Say ok", prompt)
    }

    @Test
    fun preparePrompt_keepsBlankPrompt() {
        val config = OnDeviceLlmConfig.defaultAndroid()

        val prompt = preparePrompt("   ", config)

        assertEquals("   ", prompt)
    }

    @Test
    fun stripReasoningSections_removesThinkingBlockAndTags() {
        val response =
            """
            <think>
            hidden chain
            </think>

            ok
            """.trimIndent()

        val cleaned = stripReasoningSections(response)

        assertEquals("ok", cleaned)
        assertFalse(cleaned.contains("think", ignoreCase = true))
    }

    @Test
    fun stripReasoningSections_removesDanglingThinkingTags() {
        val cleaned = stripReasoningSections("<think>ok")

        assertEquals("ok", cleaned)
    }

    @Test
    fun stripReasoningSections_keepsBlankResponse() {
        val response = "   "

        val cleaned = stripReasoningSections(response)

        assertEquals(response, cleaned)
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
