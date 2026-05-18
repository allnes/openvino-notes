package com.itlab.ai

class ResultProcessor {
    fun normalizeSummary(
        raw: String,
        sourceText: String? = null,
    ): String {
        val normalized = normalizeTextAnswer(raw)
        return if (
            sourceText != null &&
            !isAcceptableSummary(sourceText, normalized)
        ) {
            extractiveSummary(sourceText)
        } else {
            normalized
        }
    }

    fun normalizeRewrite(
        raw: String,
        sourceText: String? = null,
    ): String {
        val normalized = normalizeTextAnswer(raw)
        return if (sourceText != null && !isAcceptableRewrite(sourceText, normalized)) {
            sourceText.trim()
        } else {
            normalized
        }
    }

    fun normalizeTags(
        raw: String,
        maxTags: Int = Int.MAX_VALUE,
        sourceText: String? = null,
    ): Set<String> {
        val normalized =
            raw
                .stripLeadingAssistantLabel()
                .split(',', '\n', ';', '，')
                .map { it.cleanTag() }
                .filter { it.isUsefulTag() }
                .let { tags ->
                    if (sourceText == null) {
                        tags
                    } else {
                        val normalizedSource = sourceText.normalizeForMatching()
                        tags.filter { it.isGroundedIn(normalizedSource) }
                    }
                }

        if (sourceText == null) {
            return normalized.take(maxTags).toCollection(LinkedHashSet())
        }

        return (normalized + extractKeywordTags(sourceText, maxTags * 2))
            .distinct()
            .take(maxTags)
            .toCollection(LinkedHashSet())
    }

    fun shouldRetrySummary(
        sourceText: String,
        raw: String,
    ): Boolean =
        !isAcceptableSummary(sourceText, normalizeTextAnswer(raw))

    fun shouldRetryRewrite(
        sourceText: String,
        raw: String,
    ): Boolean =
        !isAcceptableRewrite(sourceText, normalizeTextAnswer(raw))

    fun shouldRetryTags(
        sourceText: String,
        raw: String,
        maxTags: Int,
    ): Boolean =
        normalizeGeneratedTags(raw, maxTags, sourceText).size < minOf(MIN_GENERATED_TAGS_BEFORE_FALLBACK, maxTags)

    fun extractKeywordTags(
        text: String,
        maxTags: Int,
    ): Set<String> {
        if (text.isBlank() || maxTags <= 0) {
            return emptySet()
        }
        val language = NoteLanguageDetector.detect(text)
        return wordRegex
            .findAll(text)
            .mapIndexed { index, match ->
                val rawToken = match.value.trim('-', '_', '.', ',')
                val tag = rawToken.cleanTag()
                TagCandidate(
                    tag = tag,
                    index = index,
                    score = tag.keywordScore(rawToken, language),
                )
            }
            .filter { it.tag.isUsefulTag() }
            .filterNot { it.tag in commonStopWords || it.tag in language.stopWords }
            .distinctBy { it.tag }
            .sortedWith(
                compareByDescending<TagCandidate> { it.score }
                    .thenBy { it.index },
            )
            .map { it.tag }
            .take(maxTags)
            .toCollection(LinkedHashSet())
    }

    private fun stripGeneratedMetadataSections(raw: String): String =
        raw
            .lineSequence()
            .withIndex()
            .takeWhile { (index, line) -> index == 0 || !generatedMetadataHeader.matches(line) }
            .map { it.value }
            .joinToString("\n")
            .trim()

    private fun normalizeTextAnswer(raw: String): String =
        stripGeneratedMetadataSections(raw)
            .stripLeadingAssistantLabel()
            .stripWrappingMarkdown()
            .trim()

    private fun normalizeGeneratedTags(
        raw: String,
        maxTags: Int,
        sourceText: String,
    ): Set<String> {
        val normalizedSource = sourceText.normalizeForMatching()
        return raw
            .stripLeadingAssistantLabel()
            .split(',', '\n', ';', '，')
            .map { it.cleanTag() }
            .filter { it.isUsefulTag() }
            .filter { it.isGroundedIn(normalizedSource) }
            .take(maxTags)
            .toCollection(LinkedHashSet())
    }

    private fun String.stripLeadingAssistantLabel(): String =
        replace(leadingAssistantLabel, "")
            .replace(leadingPunctuationOnlyLine, "")
            .trim()

    private fun String.cleanTag(): String =
        trim()
            .replace(leadingTagDecoration, "")
            .trim()
            .trim('"', '\'', '`', '.', ':', '-')
            .lowercase()

    private fun shouldUseSourceFallback(
        sourceText: String,
        answer: String,
    ): Boolean {
        if (answer.isBlank()) {
            return true
        }
        val language = NoteLanguageDetector.detect(sourceText)
        return language.forbiddenOutputPattern?.containsMatchIn(answer) == true
    }

    private fun isAcceptableSummary(
        sourceText: String,
        answer: String,
    ): Boolean =
        answer.isNotBlank() &&
            answer.length <= MAX_GENERATED_SUMMARY_CHARS &&
            !shouldUseSourceFallback(sourceText, answer)

    private fun isAcceptableRewrite(
        sourceText: String,
        answer: String,
    ): Boolean =
        answer.isNotBlank() &&
            !shouldUseSourceFallback(sourceText, answer)

    private fun extractiveSummary(sourceText: String): String =
        sourceText
            .trim()
            .split(sentenceBoundary)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(MAX_EXTRACTIVE_SUMMARY_CHARS)
            ?.trimEnd(',', ';', ':', '-')
            .orEmpty()

    private fun String.isUsefulTag(): Boolean {
        val value = trim()
        if (value.length !in MIN_TAG_LENGTH..MAX_TAG_LENGTH) {
            return false
        }
        if (value.contains("<|") || value.contains("*") || sentencePunctuation.containsMatchIn(value)) {
            return false
        }
        val wordCount = value.split(whitespace).count { it.isNotBlank() }
        if (wordCount > MAX_TAG_WORDS) {
            return false
        }
        return value.count { it.isLetterOrDigit() } >= MIN_TAG_LETTER_OR_DIGIT_COUNT
    }

    private fun String.isGroundedIn(normalizedSource: String): Boolean =
        split(whitespace)
            .filter { it.isNotBlank() }
            .all { word ->
                val normalizedWord = word.normalizeForMatching()
                normalizedWord.length <= 2 ||
                    normalizedSource.contains(normalizedWord) ||
                    normalizedSource.contains(normalizedWord.take(minOf(5, normalizedWord.length)))
            }

    private fun String.keywordScore(
        rawToken: String,
        language: NoteLanguage,
    ): Int {
        var score = 0
        if (salientFragments.any { contains(it, ignoreCase = true) }) {
            score += 20
        }
        if (rawToken.any { it.isUpperCase() } || rawToken.any { it.isDigit() }) {
            score += 4
        }
        if (length in 5..18) {
            score += 2
        }
        if (language == NoteLanguage.RUSSIAN && any { it in 'а'..'я' || it == 'ё' }) {
            score += 2
        }
        return score
    }

    private fun String.normalizeForMatching(): String =
        lowercase()
            .replace('ё', 'е')
            .replace(diacriticInsensitivePunctuation, "")

    private fun String.stripWrappingMarkdown(): String {
        val value = trim()
        return wrappingMarkdown
            .matchEntire(value)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: value
    }

    private data class TagCandidate(
        val tag: String,
        val index: Int,
        val score: Int,
    )

    private companion object {
        const val MIN_TAG_LENGTH = 2
        const val MAX_TAG_LENGTH = 32
        const val MAX_TAG_WORDS = 2
        const val MIN_TAG_LETTER_OR_DIGIT_COUNT = 2
        const val MIN_GENERATED_TAGS_BEFORE_FALLBACK = 2
        const val MAX_GENERATED_SUMMARY_CHARS = 260
        const val MAX_EXTRACTIVE_SUMMARY_CHARS = 240
        val generatedMetadataHeader =
            Regex(
                pattern = """\s*(?:[-*]\s*)?(?:\*\*)?\s*(?:tags?|summary|title)\s*(?:\*\*)?\s*:.*""",
                option = RegexOption.IGNORE_CASE,
            )
        val leadingAssistantLabel =
            Regex(
                pattern =
                    """^\s*(?:[-*]\s*)?(?:\*\*)?\s*""" +
                        """(?:(?:english|deutsche?)\s+)?""" +
                        """(?:summary|tags?|rewrite|rewritten note|резюме|теги|переписанный текст|""" +
                        """zusammenfassung|stichworte|ueberarbeitete notiz|überarbeitete notiz|""" +
                        """mots-cl[eé]s|r[eé]sum[eé]|synth[eè]se|note r[eé][eé]crite)""" +
                        """\s*(?:\*\*)?\s*:\s*""",
                option = RegexOption.IGNORE_CASE,
            )
        val leadingPunctuationOnlyLine = Regex("""^\s*[:：]\s*(?:\R+|$)""")
        val leadingTagDecoration = Regex("""^\s*(?:[-*#]|\d+[.)])\s*""")
        val wrappingMarkdown = Regex("""(?:\*\*|__)(.*)(?:\*\*|__)""", RegexOption.DOT_MATCHES_ALL)
        val whitespace = Regex("""\s+""")
        val sentencePunctuation = Regex("""[.!?]""")
        val sentenceBoundary = Regex("""(?<=[.!?。！？])\s+""")
        val wordRegex = Regex("""[\p{L}\p{N}][\p{L}\p{N}_-]{1,31}""")
        val diacriticInsensitivePunctuation = Regex("""[\p{Punct}\s]+""")
        val commonStopWords =
            setOf(
                "and",
                "the",
                "for",
                "with",
                "если",
                "для",
                "und",
                "der",
                "die",
                "das",
                "les",
                "des",
                "une",
            )
        val salientFragments =
            listOf(
                "openvino",
                "model",
                "модель",
                "датчик",
                "sensor",
                "risk",
                "risik",
                "риск",
                "demo",
                "robot",
                "prototype",
                "pressure",
                "pression",
                "temperatur",
                "qualität",
                "quality",
                "sécurité",
                "склад",
                "стенд",
                "запуск",
                "battery",
                "акку",
                "akku",
            )
    }
}
