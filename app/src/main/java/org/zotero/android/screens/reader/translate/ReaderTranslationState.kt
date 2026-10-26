package org.zotero.android.screens.reader.translate

import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateServiceType

enum class ReaderAssistMode {
    translate,
    explain,
}

data class ReaderAssistResult(
    val text: String? = null,
    val isLoading: Boolean = false,
    val error: TranslateError? = null,
) {
    val hasResult: Boolean
        get() = text != null && !isLoading
}

data class ReaderTranslationState(
    val sourceText: String,
    val mode: ReaderAssistMode,
    val targetLanguage: String,
    val service: TranslateServiceType,
    val canCreateAnnotation: Boolean,
    val translation: ReaderAssistResult = ReaderAssistResult(),
    val explanation: ReaderAssistResult = ReaderAssistResult(),
) {
    val current: ReaderAssistResult
        get() = when (mode) {
            ReaderAssistMode.translate -> translation
            ReaderAssistMode.explain -> explanation
        }
}
