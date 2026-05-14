package com.itlab.ai

class OpenVinoEngine(
    private val llmBackend: LlmInferenceBackend = UnavailableLlmBackend(),
    private val promptBuilder: NoteLlmPromptBuilder = NoteLlmPromptBuilder(),
    private val config: OnDeviceLlmConfig = OnDeviceLlmConfig.defaultAndroid(),
) {
    fun runLlmSummary(text: String): String {
        if (text.isBlank()) return ""
        return llmBackend.generate(
            prompt = promptBuilder.summaryPrompt(text),
            maxNewTokens = config.summaryMaxNewTokens,
        )
    }

    fun runLlmTagging(text: String): String {
        if (text.isBlank()) return ""
        return llmBackend.generate(
            prompt = promptBuilder.tagsPrompt(text),
            maxNewTokens = config.tagsMaxNewTokens,
        )
    }
}
