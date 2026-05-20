package com.itlab.ai

import com.itlab.domain.ai.RewriteStyle
import kotlin.math.max

class NoteLlmPromptBuilder(
    private val config: OnDeviceLlmConfig = OnDeviceLlmConfig.defaultAndroid(),
) {
    fun summaryPrompt(
        text: String,
        maxInputTokens: Int = config.summaryMaxInputTokens,
    ): String {
        val note = trimInput(text, maxInputTokens)
        val language = NoteLanguageDetector.detect(note)
        return chatPrompt(
            """
            ${language.summaryInstruction}
            Detected note language: ${language.displayName}.
            Answer only in ${language.answerInstruction}.
            Start immediately with the final summary.
            Use at most 25 words and end with a complete sentence.
            Do not translate names, locations, dates, times, numbers, or product names.
            Include the most important who/what/when/where fact if it is present.
            Return only the summary, without markdown, analysis, acknowledgements, or a preamble.

            Note:
            $note

            ${language.summaryCue}:
            """.trimIndent(),
        )
    }

    fun summaryRetryPrompt(
        text: String,
        previousAnswer: String,
        maxInputTokens: Int = config.summaryMaxInputTokens,
    ): String {
        val note = trimInput(text, maxInputTokens)
        val language = NoteLanguageDetector.detect(note)
        return chatPrompt(
            """
            The previous summary was invalid because it was empty, too long, repeated, or used the wrong language.
            Rewrite it once more.
            ${language.summaryInstruction}
            Answer only in ${language.answerInstruction}.
            Use at most 20 words.
            Keep names, locations, dates, times, numbers, and product names unchanged.
            Return only the corrected summary. Do not explain the correction.

            Invalid previous answer:
            $previousAnswer

            Note:
            $note

            ${language.summaryCue}:
            """.trimIndent(),
        )
    }

    fun tagsPrompt(
        text: String,
        maxInputTokens: Int = config.tagsMaxInputTokens,
        maxTags: Int = config.maxTags,
    ): String {
        val note = trimInput(text, maxInputTokens)
        val language = NoteLanguageDetector.detect(note)
        return chatPrompt(
            """
            ${language.tagsInstruction}
            Suggest up to $maxTags complete topic tags for the note.
            Detected note language: ${language.displayName}.
            Prefer tags in ${language.answerInstruction}.
            Use complete words copied from the note when possible.
            Preserve proper nouns and product names exactly when they are useful tags.
            Do not abbreviate. Do not output single letters or initials.
            Each tag must be at most two words and 32 characters.
            ${language.tagExample}
            Return only a comma-separated tag list. Use lowercase tags except proper nouns.
            Do not add explanations, analysis, acknowledgements, or a preamble.

            Note:
            $note

            ${language.tagsCue}:
            """.trimIndent(),
        )
    }

    fun tagsRetryPrompt(
        text: String,
        previousAnswer: String,
        maxInputTokens: Int = config.tagsMaxInputTokens,
        maxTags: Int = config.maxTags,
    ): String {
        val note = trimInput(text, maxInputTokens)
        val language = NoteLanguageDetector.detect(note)
        return chatPrompt(
            """
            The previous tag list was invalid because it contained sentences, ungrounded words, or the wrong language.
            Rewrite the tags once more.
            ${language.tagsInstruction}
            Suggest up to $maxTags tags.
            Use only words that appear in the note when possible.
            Each tag must be at most two words and 32 characters.
            Return only comma-separated tags. Do not explain the correction.
            ${language.tagExample}

            Invalid previous answer:
            $previousAnswer

            Note:
            $note

            ${language.tagsCue}:
            """.trimIndent(),
        )
    }

    fun rewritePrompt(
        text: String,
        style: RewriteStyle,
        maxInputTokens: Int = config.rewriteMaxInputTokens,
    ): String {
        val note = trimInput(text, maxInputTokens)
        val language = NoteLanguageDetector.detect(note)
        return chatPrompt(
            """
            ${rewriteInstruction(style, language)}
            Detected note language: ${language.displayName}.
            Answer only in ${language.answerInstruction}.
            Do not summarize or translate the note.
            Preserve every named person, location, deadline, number, condition, and user intent.
            Keep concrete words from the note when they carry facts, names, dates, places, or product names.
            If the note is already short, clean it up without changing its facts.
            Output only the rewritten note text.
            Do not copy any instruction, label, markdown, analysis, acknowledgement, or preamble from this prompt.

            Note:
            $note

            ${language.rewriteCue}:
            """.trimIndent(),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun rewriteRetryPrompt(
        text: String,
        style: RewriteStyle,
        previousAnswer: String,
        maxInputTokens: Int = config.rewriteMaxInputTokens,
    ): String {
        val note = trimInput(text, maxInputTokens)
        val language = NoteLanguageDetector.detect(note)
        return chatPrompt(
            """
            The previous rewrite was invalid because it used the wrong language, lost facts, or included artifacts.
            Rewrite the original note again, ignoring the invalid previous answer.
            ${rewriteInstruction(style, language)}
            Answer only in ${language.answerInstruction}.
            Preserve every named person, location, deadline, number, condition, and user intent.
            Keep concrete words from the note when they carry facts, names, dates, places, or product names.
            Output only the corrected note text.
            Do not copy any instruction, label, markdown, analysis, acknowledgement, or preamble from this prompt.

            Note:
            $note

            ${language.rewriteCue}:
            """.trimIndent(),
        )
    }

    private fun chatPrompt(instruction: String): String = instruction

    private fun rewriteInstruction(
        style: RewriteStyle,
        language: NoteLanguage,
    ): String =
        when (style) {
            RewriteStyle.CLEANUP -> language.rewriteInstruction
        }

    private fun trimInput(
        text: String,
        maxInputTokens: Int,
    ): String =
        text
            .trim()
            .take(minOf(config.maxInputChars, max(1, maxInputTokens) * config.approximateCharsPerToken))
}
