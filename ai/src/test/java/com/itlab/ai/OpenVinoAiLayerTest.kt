package com.itlab.ai

import com.itlab.domain.ai.RewriteStyle
import com.ovx.openvino.genai.AndroidPipelineProperties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class OpenVinoAiLayerTest {
    @Test
    fun normalizeSummary_trimsSpaces() {
        val processor = ResultProcessor()

        val result = processor.normalizeSummary("  short summary  ")

        assertEquals("short summary", result)
    }

    @Test
    fun normalizeSummary_removesGeneratedMetadataSections() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                """
                Short summary.
                **Tags**: android, openvino
                """.trimIndent(),
            )

        assertEquals("Short summary.", result)
    }

    @Test
    fun normalizeSummary_removesAssistantLabel() {
        val processor = ResultProcessor()

        val result = processor.normalizeSummary("Summary: Марина проверяет стенд в Казани.")

        assertEquals("Марина проверяет стенд в Казани.", result)
    }

    @Test
    fun normalizeSummary_removesWrappingMarkdown() {
        val processor = ResultProcessor()

        val result = processor.normalizeSummary("**Frau Müller prüft OpenVINO in Leipzig.**")

        assertEquals("Frau Müller prüft OpenVINO in Leipzig.", result)
    }

    @Test
    fun noteLanguageDetector_prefersGermanUmlautsBeforeFrenchAccents() {
        val result = NoteLanguageDetector.detect("Frau Müller prüft OpenVINO für Leipzig.")

        assertEquals(NoteLanguage.GERMAN, result)
    }

    @Test
    fun noteLanguageDetector_keepsFrenchAccentDetection() {
        val result = NoteLanguageDetector.detect("Claire prépare une revue à Lyon avant mercredi.")

        assertEquals(NoteLanguage.FRENCH, result)
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
    fun normalizeTags_respectsMaxTags() {
        val processor = ResultProcessor()

        val result = processor.normalizeTags("one, two, three", maxTags = 2)

        assertEquals(setOf("one", "two"), result)
    }

    @Test
    fun normalizeTags_removesLabelsBulletsHashesAndSemicolons() {
        val processor = ResultProcessor()

        val result = processor.normalizeTags("Tags: #OpenVINO; - склад; 2) риск", maxTags = 4)

        assertEquals(setOf("openvino", "склад", "риск"), result)
    }

    @Test
    fun normalizeTags_withSourceDropsUngroundedItemsAndAddsKeywordFallback() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeTags(
                raw = "Tags: maydeterminingfielddemo, OpenVINO, drchendr, labb",
                maxTags = 4,
                sourceText =
                    "Maya is planning a field demo for the hospital logistics robot. " +
                        "She must confirm the OpenVINO build and prepare battery packs.",
            )

        assertEquals(setOf("openvino", "demo", "robot", "battery"), result)
    }

    @Test
    fun normalizeTags_withSourceFiltersLongSentenceTags() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeTags(
                raw = "frau muller qualités prudence halle 2 openvino tempéraux risques",
                maxTags = 4,
                sourceText =
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. " +
                        "Jonas und Aylin testen die OpenVINO-Auswertung und Temperatursensoren.",
            )

        assertTrue(result.any { it.contains("qualität") })
        assertTrue(result.any { it.contains("openvino") || it.contains("temperatur") })
        assertTrue(result.all { it.length <= 32 })
    }

    @Test
    fun normalizeRewrite_removesGeneratedMetadataSections() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeRewrite(
                """
                Review Android demo on Monday.
                **tags**: android, demo, monday
                **summary**: review demo.
                """.trimIndent(),
            )

        assertEquals("Review Android demo on Monday.", result)
    }

    @Test
    fun normalizeRewrite_removesAssistantLabel() {
        val processor = ResultProcessor()

        val result = processor.normalizeRewrite("Rewritten note: Check Lab B at 14:00.")

        assertEquals("Check Lab B at 14:00.", result)
    }

    @Test
    fun normalizeSummary_withSourceFallsBackWhenGermanAnswerDriftsToFrench() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw = "Frau Müller coordonne une qualité en Leipzig.",
                sourceText = "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. Danach folgt der Test.",
            )

        assertEquals("Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.", result)
    }

    @Test
    fun normalizeSummary_withSourceFallsBackWhenSummaryLoopsTooLong() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw = "La qualité de ".repeat(30),
                sourceText = "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. Danach folgt der Test.",
            )

        assertEquals("Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.", result)
    }

    @Test
    fun normalizeSummary_withSourceFallsBackWhenAnswerIsUnfinished() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw = "Марина проверяет пилотный стенд и записывает три риска: задержка поставки, шумные",
                sourceText =
                    "Марина проверяет OpenVINO-стенд в Казани во вторник в 09:30. " +
                        "Иван записывает три риска для команды.",
            )

        assertEquals("Марина проверяет OpenVINO-стенд в Казани во вторник в 09:30.", result)
    }

    @Test
    fun normalizeSummary_withSourceFallsBackWhenAnswerEndsAtAbbreviation() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw = "Maya must send Dr.",
                sourceText = "Maya sends Dr. Chen the risk note before Friday 14:00.",
            )

        assertEquals("Maya sends Dr. Chen the risk note before Friday 14:00.", result)
    }

    @Test
    fun normalizeSummary_withSourceRepairsLanguageSpecificArtifacts() {
        val processor = ResultProcessor()

        val german =
            processor.normalizeSummary(
                raw = "Fur die Qualitaetspruefung in Leipzig ist Frau Müller verantwortlich.",
                sourceText = "Frau Müller koordiniert die Qualitätsprüfung in Leipzig.",
            )

        assertEquals("Für die Qualitätsprüfung in Leipzig ist Frau Müller verantwortlich.", german)
    }

    @Test
    fun normalizeSummary_withRussianSourcePrefersExtractiveGrounding() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw = "Марина проверяет пилотного стенда в Казани в 09:30.",
                sourceText = "Марина ведет запуск пилотного стенда в Казани в 09:30.",
            )

        assertEquals("Марина ведет запуск пилотного стенда в Казани в 09:30.", result)
    }

    @Test
    fun normalizeRewrite_withSourceRepairsLanguageSpecificArtifacts() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeRewrite(
                raw = "Марина проверяет пилотного стенда в Казани в 09:30.",
                sourceText = "Марина ведет запуск пилотного стенда в Казани в 09:30.",
            )

        assertEquals("Марина проверяет пилотный стенд в Казани в 09:30.", result)
    }

    @Test
    fun normalizeSummary_withSourceCompactsMultiSentenceModelAnswer() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw =
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. " +
                        "Jonas und Aylin testen OpenVINO am Donnerstag um 08:15. " +
                        "Wenn der Lärmpegel steigt, wird der Versuch in Halle 2 verschoben.",
                sourceText =
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. " +
                        "Am Donnerstag um 08:15 testen Jonas und Aylin OpenVINO in Halle 2. " +
                        "Danach dokumentiert das Team offene Risiken.",
            )

        assertEquals(
            "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. " +
                "Jonas und Aylin testen OpenVINO am Donnerstag um 08:15.",
            result,
        )
    }

    @Test
    fun normalizeSummary_withSourceFallsBackWhenAnswerAddsGenericClaim() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeSummary(
                raw =
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig, " +
                        "und die Risiken werden in der Regel von der Teamliste abgeleitet.",
                sourceText =
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig. " +
                        "Jonas testet OpenVINO am Donnerstag um 08:15.",
            )

        assertEquals("Jonas testet OpenVINO am Donnerstag um 08:15.", result)
    }

    @Test
    fun normalizeSummary_withSourceSelectsUsefulFallbackFactsAcrossLanguages() {
        val processor = ResultProcessor()
        val cases =
            listOf(
                SummaryFallbackCase(
                    raw = "the model and risk",
                    source = "Заметки к встрече. Марина проверяет OpenVINO-стенд в Казани во вторник в 09:30.",
                    genericFirstSentence = "Заметки к встрече.",
                    expectedFacts = listOf("Марина", "09:30"),
                ),
                SummaryFallbackCase(
                    raw = "Марина проверяет стенд.",
                    source =
                        "Meeting notes. Maya must confirm the OpenVINO build before Friday 14:00 " +
                            "if Lab B is closed.",
                    genericFirstSentence = "Meeting notes.",
                    expectedFacts = listOf("Maya", "14:00"),
                ),
                SummaryFallbackCase(
                    raw = "Frau Müller coordonne une qualité en Leipzig.",
                    source = "Notiz. Frau Müller testet OpenVINO in Leipzig am Donnerstag um 08:15.",
                    genericFirstSentence = "Notiz.",
                    expectedFacts = listOf("Müller", "08:15"),
                ),
                SummaryFallbackCase(
                    raw = "Frau Müller muss prüfen.",
                    source = "Note. Claire vérifie le modèle OpenVINO à Lyon avant mercredi 16:45.",
                    genericFirstSentence = "Note.",
                    expectedFacts = listOf("Claire", "16:45"),
                ),
            )

        cases.forEach { testCase ->
            val result =
                processor.normalizeSummary(
                    raw = testCase.raw,
                    sourceText = testCase.source,
                )

            assertFalse(result, result.equals(testCase.genericFirstSentence, ignoreCase = true))
            testCase.expectedFacts.forEach { fact ->
                assertTrue("$result does not contain $fact", result.contains(fact, ignoreCase = true))
            }
        }
    }

    @Test
    fun normalizeRewrite_withSourceFallsBackWhenGermanAnswerDriftsToFrench() {
        val processor = ResultProcessor()
        val source = "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig."

        val result =
            processor.normalizeRewrite(
                raw = "Frau Müller coordonne une qualité en Leipzig.",
                sourceText = source,
            )

        assertEquals(source, result)
    }

    @Test
    fun normalizeRewrite_withSourceFallsBackWhenAnswerIsUnfinished() {
        val processor = ResultProcessor()
        val source = "Maya confirms the OpenVINO build before Friday 14:00."

        val result =
            processor.normalizeRewrite(
                raw = "Maya confirms the OpenVINO build before Friday 14",
                sourceText = source,
            )

        assertEquals(source, result)
    }

    @Test
    fun normalizeRewrite_withSourceFallbackCleansPunctuationSpacing() {
        val processor = ResultProcessor()

        val result =
            processor.normalizeRewrite(
                raw = "Марина проверяет стенд.",
                sourceText = "Maya   confirms OpenVINO build ;  Friday 14:00 .",
            )

        assertEquals("Maya confirms OpenVINO build; Friday 14:00.", result)
    }

    @Test
    fun normalizeRewrite_withSourceFallsBackWhenPromptInstructionIsEchoed() {
        val processor = ResultProcessor()
        val source = "Discuss roadmap openvino tasks, groceries, and call Bob."

        val result =
            processor.normalizeRewrite(
                raw = "Start immediately with the rewritten note.",
                sourceText = source,
            )

        assertEquals(source, result)
    }

    @Test
    fun normalizeRewrite_withSourceFallsBackWhenRequiredFactsAreLost() {
        val processor = ResultProcessor()
        val source = "Discuss roadmap openvino tasks, groceries, and call Bob."

        val result =
            processor.normalizeRewrite(
                raw = "Start the work soon.",
                sourceText = source,
            )

        assertEquals(source, result)
    }

    @Test
    fun summarize_returnsTrimmedSummary() =
        runBlocking {
            val backend = RecordingLlmBackend("  Summary text.  ")
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.summarize(
                    text = "Long note",
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().summaryMaxInputTokens,
                    maxNewTokens = OnDeviceLlmConfig.defaultAndroid().summaryMaxNewTokens,
                )

            assertEquals("Summary text.", result)
            assertEquals(OnDeviceLlmConfig.defaultAndroid().summaryMaxNewTokens, backend.lastMaxNewTokens)
            assertEquals(LlmGenerationIntent.Summary, backend.lastIntent)
            assertTrue(backend.lastPrompt.orEmpty().contains("Summarize the note"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Detected note language: English"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Answer only in English"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Do not translate names"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Long note"))
        }

    @Test
    fun summarize_retriesInvalidModelAnswerBeforeFallback() =
        runBlocking {
            val backend =
                RecordingLlmBackend(
                    "Frau Müller coordonne une qualité en Leipzig.",
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.",
                )
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.summarize(
                    text = "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.",
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().summaryMaxInputTokens,
                    maxNewTokens = OnDeviceLlmConfig.defaultAndroid().summaryMaxNewTokens,
                )

            assertEquals("Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.", result)
            assertEquals(2, backend.generateCallCount)
            assertTrue(backend.prompts[1].contains("previous summary was invalid", ignoreCase = true))
        }

    @Test
    fun suggestTags_normalizesCaseAndSeparators() =
        runBlocking {
            val backend = RecordingLlmBackend(" Kotlin, Notes\nAI ")
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.suggestTags(
                    text = "Kotlin Notes AI OpenVINO note",
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().tagsMaxInputTokens,
                    maxTags = 3,
                )

            assertEquals(setOf("kotlin", "notes", "ai"), result)
            assertEquals(OnDeviceLlmConfig.defaultAndroid().tagsMaxNewTokens, backend.lastMaxNewTokens)
            assertEquals(LlmGenerationIntent.Tags, backend.lastIntent)
            assertTrue(backend.lastPrompt.orEmpty().contains("Suggest up to"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Prefer tags in English"))
            assertTrue(backend.lastPrompt.orEmpty().contains("OpenVINO note"))
        }

    @Test
    fun suggestTags_retriesWhenGeneratedTagsAreNotGrounded() =
        runBlocking {
            val backend =
                RecordingLlmBackend(
                    "frau muller qualités prudence halle 2 openvino tempéraux risques",
                    "Qualitätsprüfung, OpenVINO, Leipzig",
                )
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.suggestTags(
                    text = "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig mit OpenVINO.",
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().tagsMaxInputTokens,
                    maxTags = 3,
                )

            assertEquals(setOf("qualitätsprüfung", "openvino", "leipzig"), result)
            assertEquals(2, backend.generateCallCount)
            assertTrue(backend.prompts[1].contains("previous tag list was invalid", ignoreCase = true))
        }

    @Test
    fun rewrite_returnsTrimmedRewrite() =
        runBlocking {
            val backend = RecordingLlmBackend("  Better note.  ")
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.rewrite(
                    text = "Draft note",
                    style = RewriteStyle.CLEANUP,
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().rewriteMaxInputTokens,
                    maxNewTokens = OnDeviceLlmConfig.defaultAndroid().rewriteMaxNewTokens,
                )

            assertEquals("Better note.", result)
            assertEquals(OnDeviceLlmConfig.defaultAndroid().rewriteMaxNewTokens, backend.lastMaxNewTokens)
            assertEquals(LlmGenerationIntent.Rewrite, backend.lastIntent)
            assertTrue(backend.lastPrompt.orEmpty().contains("Rewrite the note"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Do not summarize or translate"))
            assertFalse(backend.lastPrompt.orEmpty().contains("Start immediately with the rewritten note"))
            assertTrue(backend.lastPrompt.orEmpty().contains("Draft note"))
        }

    @Test
    fun rewrite_retriesInvalidModelAnswerBeforeFallback() =
        runBlocking {
            val backend =
                RecordingLlmBackend(
                    "Frau Müller coordonne une qualité en Leipzig.",
                    "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.",
                )
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.rewrite(
                    text = "Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.",
                    style = RewriteStyle.CLEANUP,
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().rewriteMaxInputTokens,
                    maxNewTokens = OnDeviceLlmConfig.defaultAndroid().rewriteMaxNewTokens,
                )

            assertEquals("Frau Müller koordiniert eine Qualitätsprüfung in Leipzig.", result)
            assertEquals(2, backend.generateCallCount)
            assertTrue(backend.prompts[1].contains("previous rewrite was invalid", ignoreCase = true))
        }

    @Test
    fun rewrite_retriesPromptEchoAndDoesNotApplyBadRetry() =
        runBlocking {
            val source = "Discuss roadmap openvino tasks, groceries, and call Bob."
            val backend =
                RecordingLlmBackend(
                    "Start immediately with the rewritten note.",
                    "Start the work soon.",
                )
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = backend),
                    ResultProcessor(),
                )

            val result =
                service.rewrite(
                    text = source,
                    style = RewriteStyle.CLEANUP,
                    maxInputTokens = OnDeviceLlmConfig.defaultAndroid().rewriteMaxInputTokens,
                    maxNewTokens = OnDeviceLlmConfig.defaultAndroid().rewriteMaxNewTokens,
                )

            assertEquals(source, result)
            assertEquals(2, backend.generateCallCount)
            assertFalse(backend.prompts[1].contains("Start immediately with the rewritten note"))
        }

    @Test
    fun tagIMGs_delegatesToImageTaggingBackend() =
        runBlocking {
            val imageBackend = RecordingImageTaggingBackend(setOf("bus", "person"))
            val service =
                OpenVinoNoteAiService(
                    OpenVinoEngine(llmBackend = RecordingLlmBackend("unused")),
                    ResultProcessor(),
                    imageTaggingBackend = imageBackend,
                )

            val result = service.tagIMGs(listOf("/tmp/bus.jpg"))

            assertEquals(setOf("bus", "person"), result)
            assertEquals(listOf("/tmp/bus.jpg"), imageBackend.lastSources)
        }

    @Test
    fun yoloOutputParser_returnsTagsWithClassAwareNmsAndConfidenceFilter() {
        val config =
            OnDeviceVisionConfig
                .defaultAndroid()
                .copy(confidenceThreshold = 0.35f, iouThreshold = 0.45f, maxDetections = 4)
        val parser = YoloOutputParser(config)
        val outputData =
            floatArrayOf(
                0f,
                0f,
                10f,
                10f,
                0.90f,
                0f,
                1f,
                1f,
                11f,
                11f,
                0.80f,
                1f,
                20f,
                20f,
                30f,
                30f,
                0.34f,
                2f,
                40f,
                40f,
                50f,
                50f,
                0.70f,
                3f,
            )

        val result = parser.parseTags(outputData, listOf("bus", "person", "car", "traffic light"))

        assertEquals(setOf("bus", "person", "traffic light"), result)
    }

    @Test
    fun onDeviceVisionModelSelector_usesStandardModelForCapableDevices() {
        val model =
            OnDeviceVisionModelSelector.select(
                OnDeviceVisionConfig.defaultAndroid(),
                OnDeviceVisionDeviceProfile(cpuCores = 8, totalRamMb = 4096),
            )

        assertEquals("standard", model.id)
    }

    @Test
    fun onDeviceVisionModelSelector_usesCompactModelForWeakDevices() {
        val model =
            OnDeviceVisionModelSelector.select(
                OnDeviceVisionConfig.defaultAndroid(),
                OnDeviceVisionDeviceProfile(cpuCores = 2, totalRamMb = 1024),
            )

        assertEquals("compact", model.id)
    }

    @Test
    fun onDeviceVisionModelSelector_respectsExplicitModelPreference() {
        val model =
            OnDeviceVisionModelSelector.select(
                OnDeviceVisionConfig.defaultAndroid().copy(preferredModelId = "compact"),
                OnDeviceVisionDeviceProfile(cpuCores = 8, totalRamMb = 4096),
            )

        assertEquals("compact", model.id)
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
    fun noteLlmPromptBuilder_keepsComplexMultilingualFactsInPrompts() {
        val builder = NoteLlmPromptBuilder()
        val notes =
            listOf(
                "Марина проверяет стенд в Казани во вторник в 09:30.",
                "Maya sends Dr. Chen the Lab B risk note before Friday 14:00.",
                "Frau Müller prüft in Leipzig um 08:15 den Akku.",
                "Claire prépare une revue à Lyon avant mercredi 16:45.",
            )

        notes.forEach { note ->
            val summaryPrompt = builder.summaryPrompt(note)
            val tagsPrompt = builder.tagsPrompt(note)
            val rewritePrompt = builder.rewritePrompt(note, RewriteStyle.CLEANUP)

            assertTrue(summaryPrompt.contains(note))
            assertTrue(tagsPrompt.contains(note))
            assertTrue(rewritePrompt.contains(note))
            assertTrue(summaryPrompt.contains("Answer only in"))
            assertTrue(rewritePrompt.contains("Preserve every named person"))
        }
    }

    @Test
    fun androidPipelineProperties_enablesAndroidCacheAndFastCpuSettings() {
        val cacheDir = createTempDirectory("openvino-genai-cache").toFile()
        val config =
            OnDeviceLlmConfig.defaultAndroid().copy(
                inferenceNumThreads = 4,
                numStreams = 1,
                kvCachePrecision = "u8",
                dynamicQuantizationGroupSize = 32,
            )

        val properties =
            AndroidPipelineProperties
                .cpuLatency(
                    cacheDir,
                    AndroidPipelineProperties
                        .CpuLatencyOptions
                        .builder()
                        .numStreams(config.numStreams.toLong())
                        .inferenceNumThreads(config.inferenceNumThreads.toLong())
                        .kvCachePrecision(config.kvCachePrecision)
                        .dynamicQuantizationGroupSize(config.dynamicQuantizationGroupSize.toLong())
                        .build(),
                ).toMap()

        assertEquals(cacheDir.absolutePath, properties["CACHE_DIR"])
        assertEquals("SDPA", properties["ATTENTION_BACKEND"])
        assertEquals("LATENCY", properties["PERFORMANCE_HINT"])
        assertEquals(true, properties["ENABLE_MMAP"])
        assertEquals(1L, properties["NUM_STREAMS"])
        assertEquals(4L, properties["INFERENCE_NUM_THREADS"])
        assertFalse(properties.containsKey("MAX_PROMPT_LEN"))
        assertFalse(properties.containsKey("MIN_RESPONSE_LEN"))
        assertEquals("u8", properties["KV_CACHE_PRECISION"])
        assertEquals(32L, properties["DYNAMIC_QUANTIZATION_GROUP_SIZE"])
    }

    @Test
    fun defaultAndroidInferenceThreads_usesMoreThanOneThreadOnModernPhones() {
        assertEquals(4, OnDeviceLlmConfig.defaultAndroidInferenceThreads(8))
        assertEquals(3, OnDeviceLlmConfig.defaultAndroidInferenceThreads(4))
        assertEquals(1, OnDeviceLlmConfig.defaultAndroidInferenceThreads(1))
    }

    @Test
    fun openVinoEngine_returnsEmptyResultForBlankInputWithoutCallingBackend() {
        val backend = RecordingLlmBackend("unused")
        val engine = OpenVinoEngine(llmBackend = backend)

        assertEquals("", engine.runLlmSummary("   "))
        assertEquals("", engine.runLlmTagging("\n\t"))
        assertEquals("", engine.runLlmRewrite(" ", RewriteStyle.CLEANUP))
        assertEquals(null, backend.lastPrompt)
    }

    @Test
    fun openVinoEngine_releasesBackend() {
        val backend = RecordingLlmBackend("unused")
        val engine = OpenVinoEngine(llmBackend = backend)

        engine.release()

        assertTrue(backend.releaseCalled)
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
        private vararg val responses: String,
    ) : LlmInferenceBackend {
        var lastPrompt: String? = null
            private set
        var lastMaxNewTokens: Int? = null
            private set
        var lastIntent: LlmGenerationIntent? = null
            private set
        var releaseCalled: Boolean = false
            private set
        val prompts = mutableListOf<String>()
        val generateCallCount: Int
            get() = prompts.size

        override fun generate(
            prompt: String,
            maxNewTokens: Int,
            intent: LlmGenerationIntent,
        ): String {
            lastPrompt = prompt
            lastMaxNewTokens = maxNewTokens
            lastIntent = intent
            prompts += prompt
            val responseIndex = (generateCallCount - 1).coerceAtMost(responses.lastIndex)
            return responses.getOrElse(responseIndex) { "" }
        }

        override fun release() {
            releaseCalled = true
        }
    }

    private class RecordingImageTaggingBackend(
        private val tags: Set<String>,
    ) : ImageTaggingBackend {
        var lastSources: List<String> = emptyList()
            private set

        override suspend fun tagImages(imageSources: List<String>): Set<String> {
            lastSources = imageSources
            return tags
        }
    }

    private data class SummaryFallbackCase(
        val raw: String,
        val source: String,
        val genericFirstSentence: String,
        val expectedFacts: List<String>,
    )
}
