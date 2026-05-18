package com.itlab.ai

internal enum class NoteLanguage(
    val displayName: String,
    val answerInstruction: String,
    val summaryCue: String,
    val tagsCue: String,
    val rewriteCue: String,
    val summaryInstruction: String,
    val tagsInstruction: String,
    val rewriteInstruction: String,
    val tagExample: String,
    val stopWords: Set<String>,
    val forbiddenOutputPattern: Regex?,
) {
    RUSSIAN(
        displayName = "Russian",
        answerInstruction = "Russian using Cyrillic",
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
        forbiddenOutputPattern = Regex("""\b(the|and|with|pour|avec|avant|une|des|les)\b""", RegexOption.IGNORE_CASE),
    ),
    ENGLISH(
        displayName = "English",
        answerInstruction = "English",
        summaryCue = "English summary",
        tagsCue = "English tags",
        rewriteCue = "Rewritten note",
        summaryInstruction = "Summarize the note in one concise English sentence.",
        tagsInstruction = "Suggest short English topic tags. Each tag must be 1-2 complete words, not a sentence.",
        rewriteInstruction = "Rewrite the note for clarity and readability in English.",
        tagExample = "example: robot, demo, OpenVINO, risk",
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
        forbiddenOutputPattern = Regex("""[А-Яа-яЁё]|[àâçéèêëîïôûùüÿœäöüß]""", RegexOption.IGNORE_CASE),
    ),
    GERMAN(
        displayName = "German",
        answerInstruction = "German",
        summaryCue = "Deutsche Zusammenfassung",
        tagsCue = "Stichworte",
        rewriteCue = "Ueberarbeitete Notiz",
        summaryInstruction =
            "Fasse die Notiz in genau einem kurzen deutschen Satz zusammen. Schreibe ausschliesslich Deutsch.",
        tagsInstruction =
            "Erstelle kurze deutsche Stichworte. Jedes Stichwort hat 1-2 vollstaendige Woerter, keinen Satz.",
        rewriteInstruction =
            "Formuliere die Notiz klarer und lesbarer auf Deutsch. Uebersetze sie nicht in eine andere Sprache.",
        tagExample = "Beispiel: Qualitaetspruefung, Leipzig, OpenVINO, Risiken",
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
                """coordonne|qualit[eé]|temp[eé]raux|prudence|sera|salle|[eé]preuve|fournisseur|\b(avec|pour|une|des|les|la|le|de)\b""",
                RegexOption.IGNORE_CASE,
            ),
    ),
    FRENCH(
        displayName = "French",
        answerInstruction = "French",
        summaryCue = "Résumé",
        tagsCue = "Mots-clés",
        rewriteCue = "Note réécrite",
        summaryInstruction =
            "Résume la note en une phrase courte en français. Réponds uniquement en français.",
        tagsInstruction =
            "Propose des mots-clés courts en français. Chaque mot-clé contient 1-2 mots complets, pas une phrase.",
        rewriteInstruction =
            "Réécris la note plus clairement en français. Ne la traduis pas dans une autre langue.",
        tagExample = "exemple: prototype, Lyon, pression, risques",
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
}

internal object NoteLanguageDetector {
    fun detect(text: String): NoteLanguage =
        when {
            russianRegex.containsMatchIn(text) -> NoteLanguage.RUSSIAN
            frenchRegex.containsMatchIn(text) -> NoteLanguage.FRENCH
            germanRegex.containsMatchIn(text) -> NoteLanguage.GERMAN
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
