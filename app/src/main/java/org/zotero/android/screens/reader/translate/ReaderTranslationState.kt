package org.zotero.android.screens.reader.translate

import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateServiceType

data class ReaderTranslationState(
    val sourceText: String,
    val targetLanguage: String,
    val service: TranslateServiceType,
    val canCreateAnnotation: Boolean,
    val translatedText: String? = null,
    val isLoading: Boolean = false,
    val error: TranslateError? = null,
)
