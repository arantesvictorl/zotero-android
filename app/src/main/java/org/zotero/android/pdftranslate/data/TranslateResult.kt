package org.zotero.android.pdftranslate.data

data class TranslateResult(
    val sourceText: String,
    val translatedText: String,
    val service: TranslateServiceType,
    val targetLanguage: String,
    val detectedSourceLanguage: String? = null,
)
