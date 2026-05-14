package com.itlab.ai

class GemmaPromptBuilder(
    private val config: OnDeviceLlmConfig = OnDeviceLlmConfig.gemma3SmallIt(),
) {
    fun summaryPrompt(text: String): String =
        gemmaUserTurn(
            """
            Summarize the note in 1-2 concise sentences.
            Return only the summary, without markdown or a preamble.

            Note:
            ${trimInput(text)}
            """.trimIndent(),
        )

    fun tagsPrompt(text: String): String =
        gemmaUserTurn(
            """
            Suggest up to ${config.maxTags} short tags for the note.
            Return only a comma-separated tag list. Use lowercase tags.

            Note:
            ${trimInput(text)}
            """.trimIndent(),
        )

    private fun gemmaUserTurn(instruction: String): String =
        """
        <start_of_turn>user
        $instruction
        <end_of_turn>
        <start_of_turn>model
        """.trimIndent()

    private fun trimInput(text: String): String =
        text
            .trim()
            .take(config.maxInputChars)
}
