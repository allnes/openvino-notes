package com.itlab.ai.di

import com.itlab.ai.GemmaPromptBuilder
import com.itlab.ai.LlmInferenceBackend
import com.itlab.ai.OnDeviceLlmConfig
import com.itlab.ai.OpenVinoEngine
import com.itlab.ai.OpenVinoGenAiBackend
import com.itlab.ai.OpenVinoNoteAiService
import com.itlab.ai.ResultProcessor
import com.itlab.domain.ai.NoteAiService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val aiModule =
    module {
        single { OnDeviceLlmConfig.gemma3SmallIt() }
        single { GemmaPromptBuilder(get()) }
        single<LlmInferenceBackend> { OpenVinoGenAiBackend(androidContext(), get()) }
        single { ResultProcessor() }
        single {
            OpenVinoEngine(
                llmBackend = get(),
                promptBuilder = get(),
                config = get(),
            )
        }
        single<NoteAiService> {
            OpenVinoNoteAiService(
                engine = get(),
                processor = get(),
            )
        }
    }
