package org.zotero.android.screens.reader.translate

import org.zotero.android.pdftranslate.data.LlmAction
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateServiceType

data class ReaderAssistResult(
    val text: String? = null,
    val isLoading: Boolean = false,
    val error: TranslateError? = null,
) {
    val hasResult: Boolean
        get() = text != null && !isLoading
}

/**
 * State of the bottom sheet opened from the selection menu. Its tabs are "Translate" followed by
 * the user defined LLM actions.
 */
data class ReaderTranslationState(
    val sourceText: String,
    val selectedTab: String,
    val targetLanguage: String,
    val service: TranslateServiceType,
    val canCreateAnnotation: Boolean,
    val actions: List<LlmAction>,
    val results: Map<String, ReaderAssistResult> = emptyMap(),
) {
    val current: ReaderAssistResult
        get() = results[selectedTab] ?: ReaderAssistResult()

    val isTranslateTab: Boolean
        get() = selectedTab == TRANSLATE_TAB

    val selectedAction: LlmAction?
        get() = actions.firstOrNull { it.id == selectedTab }

    companion object {
        const val TRANSLATE_TAB = "translate"
    }
}
