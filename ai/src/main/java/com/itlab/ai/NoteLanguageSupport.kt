package com.itlab.ai

internal enum class NoteLanguage(
    val displayName: String,
    private val prompts: NoteLanguagePrompts,
    private val rules: NoteLanguageRules,
) {
    RUSSIAN(
        displayName = "Russian",
        prompts =
            NoteLanguagePrompts(
                summaryCue = "Резюме",
                tagsCue = "Теги",
                rewriteCue = "Переписанный текст",
                summaryInstruction =
                    "Суммируй заметку одним коротким русским предложением. Пиши только по-русски.",
                tagsInstruction =
                    "Предложи короткие русские теги. Каждый тег: 1-2 полных слова, без предложений.",
                rewriteInstruction =
                    "Перепиши заметку яснее и аккуратнее. Пиши только по-русски.",
                tagExample = "пример: склад, OpenVINO, риски, датчики",
            ),
        rules =
            NoteLanguageRules(
                answerInstruction = "Russian using Cyrillic",
                stopWords =
                    setOf(
                        "для",
                        "если",
                        "еще",
                        "ещё",
                        "надо",
                        "нужно",
                        "должен",
                        "должна",
                        "ведет",
                        "ведёт",
                        "проверить",
                        "записать",
                        "выше",
                        "ниже",
                    ),
                forbiddenOutputPattern =
                    Regex(
                        """\b(the|and|with|pour|avec|avant|une|des|les)\b""",
                        RegexOption.IGNORE_CASE,
                    ),
            ),
    ),
    ENGLISH(
        displayName = "English",
        prompts =
            NoteLanguagePrompts(
                summaryCue = "English summary",
                tagsCue = "English tags",
                rewriteCue = "Rewritten note",
                summaryInstruction = "Summarize the note in one concise English sentence.",
                tagsInstruction =
                    "Suggest short English topic tags. Each tag must be 1-2 complete words, not a sentence.",
                rewriteInstruction = "Rewrite the note for clarity and readability in English.",
                tagExample = "example: robot, demo, OpenVINO, risk",
            ),
        rules =
            NoteLanguageRules(
                answerInstruction = "English",
                stopWords =
                    setOf(
                        "the",
                        "and",
                        "with",
                        "for",
                        "before",
                        "after",
                        "must",
                        "should",
                        "needs",
                        "need",
                        "planning",
                        "confirm",
                        "prepare",
                        "send",
                        "move",
                        "changes",
                    ),
                forbiddenOutputPattern =
                    Regex(
                        """[А-Яа-яЁё]|[àâçéèêëîïôûùüÿœäöüß]""",
                        RegexOption.IGNORE_CASE,
                    ),
            ),
    ),
    GERMAN(
        displayName = "German",
        prompts =
            NoteLanguagePrompts(
                summaryCue = "Deutsche Zusammenfassung",
                tagsCue = "Stichworte",
                rewriteCue = "Ueberarbeitete Notiz",
                summaryInstruction =
                    "Fasse die Notiz in genau einem kurzen deutschen Satz zusammen. Schreibe ausschliesslich Deutsch.",
                tagsInstruction =
                    "Erstelle kurze deutsche Stichworte. Jedes Stichwort hat 1-2 vollstaendige Woerter, keinen Satz.",
                rewriteInstruction =
                    "Formuliere die Notiz klarer und lesbarer auf Deutsch. " +
                        "Uebersetze sie nicht in eine andere Sprache.",
                tagExample = "Beispiel: Qualitaetspruefung, Leipzig, OpenVINO, Risiken",
            ),
        rules =
            NoteLanguageRules(
                answerInstruction = "German",
                stopWords =
                    setOf(
                        "der",
                        "die",
                        "das",
                        "und",
                        "mit",
                        "für",
                        "fuer",
                        "bis",
                        "soll",
                        "sollen",
                        "muss",
                        "am",
                        "im",
                        "eine",
                        "einen",
                        "den",
                        "wenn",
                        "wird",
                        "danach",
                        "braucht",
                        "koordiniert",
                        "testen",
                    ),
                forbiddenOutputPattern =
                    Regex(
                        """coordonne|qualit[eé]|temp[eé]raux|prudence|sera|salle|[eé]preuve|fournisseur|""" +
                            """\b(avec|pour|une|des|les|la|le|de)\b""",
                        RegexOption.IGNORE_CASE,
                    ),
            ),
    ),
    FRENCH(
        displayName = "French",
        prompts =
            NoteLanguagePrompts(
                summaryCue = "Résumé",
                tagsCue = "Mots-clés",
                rewriteCue = "Note réécrite",
                summaryInstruction =
                    "Résume la note en une phrase courte en français. Réponds uniquement en français.",
                tagsInstruction =
                    "Propose des mots-clés courts en français. " +
                        "Chaque mot-clé contient 1-2 mots complets, pas une phrase.",
                rewriteInstruction =
                    "Réécris la note plus clairement en français. Ne la traduis pas dans une autre langue.",
                tagExample = "exemple: prototype, Lyon, pression, risques",
            ),
        rules =
            NoteLanguageRules(
                answerInstruction = "French",
                stopWords =
                    setOf(
                        "le",
                        "la",
                        "les",
                        "des",
                        "une",
                        "avec",
                        "pour",
                        "avant",
                        "doit",
                        "elle",
                        "envoyer",
                        "vérifier",
                        "verifier",
                        "prépare",
                        "prepare",
                        "devient",
                    ),
                forbiddenOutputPattern =
                    Regex(
                        """\b(soll|sollen|wird|wenn|danach|braucht|pr[uü]fung|risiken|akku|halle|frau|herr)\b""",
                        RegexOption.IGNORE_CASE,
                    ),
            ),
    ),
    ;

    val answerInstruction: String
        get() = rules.answerInstruction
    val summaryCue: String
        get() = prompts.summaryCue
    val tagsCue: String
        get() = prompts.tagsCue
    val rewriteCue: String
        get() = prompts.rewriteCue
    val summaryInstruction: String
        get() = prompts.summaryInstruction
    val tagsInstruction: String
        get() = prompts.tagsInstruction
    val rewriteInstruction: String
        get() = prompts.rewriteInstruction
    val tagExample: String
        get() = prompts.tagExample
    val stopWords: Set<String>
        get() = rules.stopWords
    val forbiddenOutputPattern: Regex?
        get() = rules.forbiddenOutputPattern
}

private data class NoteLanguagePrompts(
    val summaryCue: String,
    val tagsCue: String,
    val rewriteCue: String,
    val summaryInstruction: String,
    val tagsInstruction: String,
    val rewriteInstruction: String,
    val tagExample: String,
)

private data class NoteLanguageRules(
    val answerInstruction: String,
    val stopWords: Set<String>,
    val forbiddenOutputPattern: Regex?,
)

internal object NoteLanguageDetector {
    fun detect(text: String): NoteLanguage =
        when {
            russianRegex.containsMatchIn(text) -> NoteLanguage.RUSSIAN
            germanRegex.containsMatchIn(text) -> NoteLanguage.GERMAN
            frenchRegex.containsMatchIn(text) -> NoteLanguage.FRENCH
            else -> NoteLanguage.ENGLISH
        }

    private val russianRegex = Regex("[А-Яа-яЁё]")
    private val frenchRegex =
        Regex(
            "\\b(le|la|les|des|une|avant|doit|avec|pour|risques?)\\b|[àâçéèêëîïôûùüÿœ]",
            RegexOption.IGNORE_CASE,
        )
    private val germanRegex =
        Regex(
            "\\b(der|die|das|und|mit|für|fuer|soll|sollen|muss|frau|herr|prüfung|pruefung)\\b|[äöüß]",
            RegexOption.IGNORE_CASE,
        )
}
