package org.zotero.android.pdftranslate.data

import java.util.Locale
import java.util.UUID

/**
 * User defined prompt shown next to "Translate" in the reader (e.g. "Explain", "Summarize").
 */
data class LlmAction(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val prompt: String = "",
    // Whether the text around the selection is sent along as {contextBefore}/{contextAfter}.
    val useContext: Boolean = true,
) {
    companion object {
        private const val EXPLAIN_ID = "explain"
        private const val SUMMARIZE_ID = "summarize"

        const val DEFAULT_EXPLAIN_PROMPT =
            "You are helping a researcher read an academic paper titled \"${TranslateSettings.PLACEHOLDER_TITLE}\".\n" +
                    "Explain the selected passage below in ${TranslateSettings.PLACEHOLDER_TARGET_LANGUAGE}. Be clear and " +
                    "concise: say what it means in simpler words, explain the key concepts, terms, " +
                    "notation or code it uses, and how it relates to the surrounding text. Do not just " +
                    "translate it. Use short paragraphs or bullet points, without tables.\n\n" +
                    "Text before the passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_CONTEXT_BEFORE}\"\"\"\n\n" +
                    "Selected passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_TEXT}\"\"\"\n\n" +
                    "Text after the passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_CONTEXT_AFTER}\"\"\""

        const val DEFAULT_SUMMARIZE_PROMPT =
            "Summarize the selected passage of the academic paper \"${TranslateSettings.PLACEHOLDER_TITLE}\" " +
                    "in ${TranslateSettings.PLACEHOLDER_TARGET_LANGUAGE}, in 3 to 5 short bullet points with its " +
                    "main ideas. The surrounding text is only there to help you understand the passage, " +
                    "do not summarize it.\n\n" +
                    "Text before the passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_CONTEXT_BEFORE}\"\"\"\n\n" +
                    "Selected passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_TEXT}\"\"\"\n\n" +
                    "Text after the passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_CONTEXT_AFTER}\"\"\""

        const val NEW_ACTION_PROMPT =
            "Answer in ${TranslateSettings.PLACEHOLDER_TARGET_LANGUAGE}.\n\n" +
                    "Selected passage:\n\"\"\"${TranslateSettings.PLACEHOLDER_TEXT}\"\"\""

        fun defaults(locale: Locale = Locale.getDefault()): List<LlmAction> {
            val portuguese = locale.language == "pt"
            return listOf(
                LlmAction(
                    id = EXPLAIN_ID,
                    name = if (portuguese) "Explicar" else "Explain",
                    prompt = DEFAULT_EXPLAIN_PROMPT,
                ),
                LlmAction(
                    id = SUMMARIZE_ID,
                    name = if (portuguese) "Resumir" else "Summarize",
                    prompt = DEFAULT_SUMMARIZE_PROMPT,
                ),
            )
        }
    }
}
