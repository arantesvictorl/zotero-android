package org.zotero.android.pdftranslate.data

data class TranslateSettings(
    val service: TranslateServiceType = TranslateServiceType.google,
    val sourceLanguage: String = TranslateLanguage.AUTO,
    val targetLanguage: String = TranslateLanguage.defaultTarget(),
    val deeplApiKey: String = "",
    val openAiApiKey: String = "",
    val openAiBaseUrl: String = DEFAULT_OPEN_AI_BASE_URL,
    val openAiModel: String = DEFAULT_OPEN_AI_MODEL,
    val openAiPrompt: String = DEFAULT_OPEN_AI_PROMPT,
    // Same as "Add translation to annotation comment" in zotero-pdf-translate.
    val addTranslationToAnnotationComment: Boolean = false,
    // Joins words split by hyphenation and removes line breaks coming from PDF text layout.
    val cleanUpSourceText: Boolean = true,
    // LLM actions always use the LLM configured above, regardless of the translation service.
    val llmActions: List<LlmAction> = LlmAction.defaults(),
    // Amount of surrounding document text sent along with the selection to LLM actions.
    val contextLength: Int = DEFAULT_CONTEXT_LENGTH,
) {
    companion object {
        const val DEFAULT_OPEN_AI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPEN_AI_MODEL = "gpt-4o-mini"
        const val PLACEHOLDER_SOURCE_LANGUAGE = "{sourceLanguage}"
        const val PLACEHOLDER_TARGET_LANGUAGE = "{targetLanguage}"
        const val PLACEHOLDER_TEXT = "{text}"
        const val PLACEHOLDER_TITLE = "{title}"
        const val PLACEHOLDER_CONTEXT_BEFORE = "{contextBefore}"
        const val PLACEHOLDER_CONTEXT_AFTER = "{contextAfter}"
        const val DEFAULT_CONTEXT_LENGTH = 2000
        const val MAX_CONTEXT_LENGTH = 20000

        // Adapted from the default GPT prompt of zotero-pdf-translate.
        const val DEFAULT_OPEN_AI_PROMPT =
            "As an academic expert with specialized knowledge in various fields, please provide a " +
                    "proficient and precise translation from $PLACEHOLDER_SOURCE_LANGUAGE to " +
                    "$PLACEHOLDER_TARGET_LANGUAGE of the academic text enclosed in 🔤. It is crucial " +
                    "to maintain the original phrase or sentence and ensure accuracy while utilizing " +
                    "the appropriate language. The text is as follows: 🔤 $PLACEHOLDER_TEXT 🔤 " +
                    "Please provide the translated result without any additional explanation and remove 🔤."
    }
}
