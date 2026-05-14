package com.itlab.ai

class NoteLlmPromptBuilder(
    private val config: OnDeviceLlmConfig = OnDeviceLlmConfig.defaultAndroid(),
) {
    fun summaryPrompt(text: String): String =
        chatPrompt(
            """
            Summarize the note in 1-2 concise sentences.
            Return only the summary, without markdown or a preamble.

            Note:
            ${trimInput(text)}
            """.trimIndent(),
        )

    fun tagsPrompt(text: String): String =
        chatPrompt(
            """
            Suggest up to ${config.maxTags} short tags for the note.
            Return only a comma-separated tag list. Use lowercase tags.

            Note:
            ${trimInput(text)}
            """.trimIndent(),
        )

    private fun chatPrompt(instruction: String): String =
        """
        <|im_start|>system
        You are a concise assistant for a notes app.
        <|im_end|>
        <|im_start|>user
        $instruction
        <|im_end|>
        <|im_start|>assistant
        """.trimIndent()

    private fun trimInput(text: String): String =
        text
            .trim()
            .take(config.maxInputChars)
}
