package com.itlab.ai

class ResultProcessor {
    fun normalizeSummary(
        raw: String,
        sourceText: String? = null,
    ): String {
        val normalized = GeneratedTextCleaner.normalizeTextAnswer(raw)
        return if (
            sourceText != null &&
            !AnswerQuality.isAcceptableSummary(sourceText, normalized)
        ) {
            AnswerQuality.extractiveSummary(sourceText)
        } else {
            normalized
        }
    }

    fun normalizeRewrite(
        raw: String,
        sourceText: String? = null,
    ): String {
        val normalized = GeneratedTextCleaner.normalizeTextAnswer(raw)
        return if (sourceText != null && !AnswerQuality.isAcceptableRewrite(sourceText, normalized)) {
            sourceText.trim()
        } else {
            normalized
        }
    }

    fun normalizeTags(
        raw: String,
        maxTags: Int = Int.MAX_VALUE,
        sourceText: String? = null,
    ): Set<String> = TagProcessor.normalizeTags(raw, maxTags, sourceText)

    fun shouldRetrySummary(
        sourceText: String,
        raw: String,
    ): Boolean = !AnswerQuality.isAcceptableSummary(sourceText, GeneratedTextCleaner.normalizeTextAnswer(raw))

    fun shouldRetryRewrite(
        sourceText: String,
        raw: String,
    ): Boolean = !AnswerQuality.isAcceptableRewrite(sourceText, GeneratedTextCleaner.normalizeTextAnswer(raw))

    fun shouldRetryTags(
        sourceText: String,
        raw: String,
        maxTags: Int,
    ): Boolean = TagProcessor.shouldRetryTags(sourceText, raw, maxTags)
}

private object AnswerQuality {
    fun isAcceptableSummary(
        sourceText: String,
        answer: String,
    ): Boolean =
        answer.isNotBlank() &&
            answer.length <= MAX_GENERATED_SUMMARY_CHARS &&
            !shouldUseSourceFallback(sourceText, answer)

    fun isAcceptableRewrite(
        sourceText: String,
        answer: String,
    ): Boolean =
        answer.isNotBlank() &&
            !shouldUseSourceFallback(sourceText, answer) &&
            !containsPromptArtifact(sourceText, answer) &&
            preservesRewriteAnchors(sourceText, answer)

    fun extractiveSummary(sourceText: String): String =
        sourceText
            .trim()
            .split(sentenceBoundary)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(MAX_EXTRACTIVE_SUMMARY_CHARS)
            ?.trimEnd(',', ';', ':', '-')
            .orEmpty()

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

    private fun containsPromptArtifact(
        sourceText: String,
        answer: String,
    ): Boolean =
        rewritePromptArtifacts.any { artifact ->
            artifact.containsMatchIn(answer) && !artifact.containsMatchIn(sourceText)
        }

    private fun preservesRewriteAnchors(
        sourceText: String,
        answer: String,
    ): Boolean {
        val anchors = extractRewriteAnchors(sourceText)
        if (anchors.isEmpty()) {
            return true
        }

        val normalizedAnswer = GeneratedTextCleaner.normalizeForMatching(answer)
        val requiredAnchors = anchors.filter { it.required }
        if (requiredAnchors.isEmpty() && anchors.size < MIN_ANCHORS_FOR_OPTIONAL_MATCHING) {
            return true
        }
        if (requiredAnchors.any { !it.matches(normalizedAnswer) }) {
            return false
        }

        val matchedAnchors = anchors.count { it.matches(normalizedAnswer) }
        val minimumMatches =
            when {
                anchors.size <= 2 -> 1
                anchors.size <= 5 -> 2
                else -> anchors.size / 2
            }
        return matchedAnchors >= minimumMatches
    }

    private fun extractRewriteAnchors(sourceText: String): List<RewriteAnchor> {
        val language = NoteLanguageDetector.detect(sourceText)
        return sourceWordRegex
            .findAll(sourceText)
            .mapIndexedNotNull { index, match ->
                val raw = match.value.trim('.', ',', ';', ':', '!', '?', '(', ')', '[', ']', '"', '\'')
                val normalized = GeneratedTextCleaner.normalizeForMatching(raw)
                if (!isUsefulRewriteAnchor(normalized, language)) {
                    null
                } else {
                    RewriteAnchor(
                        normalized = normalized,
                        required = isRequiredRewriteAnchor(index, raw, normalized),
                    )
                }
            }.distinctBy { it.normalized }
            .take(MAX_REWRITE_ANCHORS)
            .toList()
    }

    private fun isUsefulRewriteAnchor(
        normalized: String,
        language: NoteLanguage,
    ): Boolean =
        normalized.length >= MIN_REWRITE_ANCHOR_LENGTH &&
            normalized !in commonRewriteStopWords &&
            normalized !in language.stopWords

    private fun isRequiredRewriteAnchor(
        index: Int,
        raw: String,
        normalized: String,
    ): Boolean {
        val hasDigit = raw.any { it.isDigit() }
        val hasProductShape =
            raw.length > 1 &&
                raw.any { it.isUpperCase() } &&
                raw.drop(1).any { it.isUpperCase() || it.isDigit() }
        val hasProperNounShape =
            index > 0 &&
                raw.firstOrNull()?.isUpperCase() == true &&
                raw.drop(1).any { it.isLowerCase() }
        return hasDigit ||
            hasProductShape ||
            hasProperNounShape ||
            requiredRewriteFragments.any { normalized.contains(it) }
    }

    private const val MAX_GENERATED_SUMMARY_CHARS = 260
    private const val MAX_EXTRACTIVE_SUMMARY_CHARS = 240
    private const val MIN_REWRITE_ANCHOR_LENGTH = 4
    private const val MIN_ANCHORS_FOR_OPTIONAL_MATCHING = 3
    private const val MAX_REWRITE_ANCHORS = 12
    private val sentenceBoundary = Regex("""(?<=[.!?。！？])\s+""")
    private val sourceWordRegex = Regex("""[\p{L}\p{N}][\p{L}\p{N}_:+.-]{1,48}""")
    private val rewritePromptArtifacts =
        listOf(
            Regex("""\bstart immediately with the rewritten note\b""", RegexOption.IGNORE_CASE),
            Regex("""\breturn only\b""", RegexOption.IGNORE_CASE),
            Regex("""\banswer only in\b""", RegexOption.IGNORE_CASE),
            Regex("""\binvalid previous answer\b""", RegexOption.IGNORE_CASE),
            Regex("""\bprevious rewrite was invalid\b""", RegexOption.IGNORE_CASE),
            Regex("""\bdo not copy any instruction\b""", RegexOption.IGNORE_CASE),
            Regex("""\bdo not explain\b""", RegexOption.IGNORE_CASE),
            Regex("""^\s*(?:task|note|user note)\s*:""", RegexOption.IGNORE_CASE),
        )
    private val commonRewriteStopWords =
        setOf(
            "this",
            "that",
            "with",
            "from",
            "have",
            "will",
            "надо",
            "нужно",
            "если",
            "еще",
            "ещё",
            "eine",
            "einen",
            "soll",
            "muss",
            "avec",
            "pour",
            "avant",
            "doit",
        )
    private val requiredRewriteFragments =
        listOf(
            "openvino",
            "android",
            "qwen",
            "firebase",
            "gradle",
            "release",
            "debug",
            "модель",
            "опенвино",
        )

    private data class RewriteAnchor(
        val normalized: String,
        val required: Boolean,
    ) {
        fun matches(normalizedAnswer: String): Boolean =
            normalizedAnswer.contains(normalized) ||
                (normalized.length >= 6 && normalizedAnswer.contains(normalized.take(6)))
    }
}

private object GeneratedTextCleaner {
    fun normalizeTextAnswer(raw: String): String =
        stripLeadingInstructionEcho(stripLeadingAssistantLabel(stripGeneratedMetadataSections(raw)))
            .stripWrappingMarkdown()
            .trim()

    fun stripLeadingAssistantLabel(raw: String): String =
        raw
            .replace(leadingAssistantLabel, "")
            .replace(leadingPunctuationOnlyLine, "")
            .trim()

    private fun stripLeadingInstructionEcho(raw: String): String =
        raw
            .replace(leadingInstructionEcho, "")
            .trim()

    fun normalizeForMatching(text: String): String =
        text
            .lowercase()
            .replace('ё', 'е')
            .replace(diacriticInsensitivePunctuation, "")

    private fun stripGeneratedMetadataSections(raw: String): String =
        raw
            .lineSequence()
            .withIndex()
            .takeWhile { (index, line) -> index == 0 || !generatedMetadataHeader.matches(line) }
            .map { it.value }
            .joinToString("\n")
            .trim()

    private fun String.stripWrappingMarkdown(): String {
        val value = trim()
        return wrappingMarkdown
            .matchEntire(value)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: value
    }

    private val generatedMetadataHeader =
        Regex(
            pattern = """\s*(?:[-*]\s*)?(?:\*\*)?\s*(?:tags?|summary|title)\s*(?:\*\*)?\s*:.*""",
            option = RegexOption.IGNORE_CASE,
        )
    private val leadingAssistantLabel =
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
    private val leadingPunctuationOnlyLine = Regex("""^\s*[:：]\s*(?:\R+|$)""")
    private val leadingInstructionEcho =
        Regex(
            pattern =
                """^\s*(?:[-*]\s*)?(?:start immediately with the rewritten note|""" +
                    """return only the rewritten note text|output only the rewritten note text)\s*[.!:]?\s*(?:\R+|$)""",
            option = RegexOption.IGNORE_CASE,
        )
    private val wrappingMarkdown = Regex("""(?:\*\*|__)(.*)(?:\*\*|__)""", RegexOption.DOT_MATCHES_ALL)
    private val diacriticInsensitivePunctuation = Regex("""[\p{Punct}\s]+""")
}

private object TagProcessor {
    fun normalizeTags(
        raw: String,
        maxTags: Int,
        sourceText: String?,
    ): Set<String> {
        val generatedTags = generatedTags(raw, sourceText)
        if (sourceText == null) {
            return generatedTags.take(maxTags).toCollection(LinkedHashSet())
        }

        return (generatedTags + extractKeywordTags(sourceText, maxTags * 2))
            .distinct()
            .take(maxTags)
            .toCollection(LinkedHashSet())
    }

    fun shouldRetryTags(
        sourceText: String,
        raw: String,
        maxTags: Int,
    ): Boolean =
        generatedTags(raw, sourceText).take(maxTags).size <
            minOf(MIN_GENERATED_TAGS_BEFORE_FALLBACK, maxTags)

    private fun generatedTags(
        raw: String,
        sourceText: String?,
    ): List<String> {
        val sourceForMatching = sourceText?.let(GeneratedTextCleaner::normalizeForMatching)
        return GeneratedTextCleaner
            .stripLeadingAssistantLabel(raw)
            .split(',', '\n', ';', '，')
            .map(::cleanTag)
            .filter(::isUsefulTag)
            .filter { tag ->
                sourceForMatching == null || isGroundedIn(tag, sourceForMatching)
            }
    }

    private fun extractKeywordTags(
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
                val tag = cleanTag(rawToken)
                TagCandidate(
                    tag = tag,
                    index = index,
                    score = keywordScore(tag, rawToken, language),
                )
            }.filter { isUsefulTag(it.tag) }
            .filterNot { it.tag in commonStopWords || it.tag in language.stopWords }
            .distinctBy { it.tag }
            .sortedWith(
                compareByDescending<TagCandidate> { it.score }
                    .thenBy { it.index },
            ).map { it.tag }
            .take(maxTags)
            .toCollection(LinkedHashSet())
    }

    private fun cleanTag(raw: String): String =
        raw
            .trim()
            .replace(leadingTagDecoration, "")
            .trim()
            .trim('"', '\'', '`', '.', ':', '-')
            .lowercase()

    private fun isUsefulTag(value: String): Boolean {
        val wordCount = value.split(whitespace).count { it.isNotBlank() }
        return value.length in MIN_TAG_LENGTH..MAX_TAG_LENGTH &&
            !value.contains("<|") &&
            !value.contains("*") &&
            !sentencePunctuation.containsMatchIn(value) &&
            wordCount <= MAX_TAG_WORDS &&
            value.count { it.isLetterOrDigit() } >= MIN_TAG_LETTER_OR_DIGIT_COUNT
    }

    private fun isGroundedIn(
        tag: String,
        normalizedSource: String,
    ): Boolean =
        tag
            .split(whitespace)
            .filter { it.isNotBlank() }
            .all { word ->
                val normalizedWord = GeneratedTextCleaner.normalizeForMatching(word)
                normalizedWord.length <= 2 ||
                    normalizedSource.contains(normalizedWord) ||
                    normalizedSource.contains(normalizedWord.take(minOf(5, normalizedWord.length)))
            }

    private fun keywordScore(
        tag: String,
        rawToken: String,
        language: NoteLanguage,
    ): Int {
        var score = 0
        if (salientFragments.any { tag.contains(it, ignoreCase = true) }) {
            score += 20
        }
        if (rawToken.any { it.isUpperCase() } || rawToken.any { it.isDigit() }) {
            score += 4
        }
        if (tag.length in 5..18) {
            score += 2
        }
        if (language == NoteLanguage.RUSSIAN && tag.any { it in 'а'..'я' || it == 'ё' }) {
            score += 2
        }
        return score
    }

    private data class TagCandidate(
        val tag: String,
        val index: Int,
        val score: Int,
    )

    private const val MIN_TAG_LENGTH = 2
    private const val MAX_TAG_LENGTH = 32
    private const val MAX_TAG_WORDS = 2
    private const val MIN_TAG_LETTER_OR_DIGIT_COUNT = 2
    private const val MIN_GENERATED_TAGS_BEFORE_FALLBACK = 2
    private val leadingTagDecoration = Regex("""^\s*(?:[-*#]|\d+[.)])\s*""")
    private val whitespace = Regex("""\s+""")
    private val sentencePunctuation = Regex("""[.!?]""")
    private val wordRegex = Regex("""[\p{L}\p{N}][\p{L}\p{N}_-]{1,31}""")
    private val commonStopWords =
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
    private val salientFragments =
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
