package org.zotero.android.pdftranslate.data

import java.util.Locale

/**
 * Languages offered in the UI. Codes follow BCP 47 (Google Translate style), each service maps them
 * to its own format.
 */
object TranslateLanguage {
    const val AUTO = "auto"

    val targetCodes = listOf(
        "en", "pt-BR", "pt-PT", "es", "fr", "de", "it", "nl", "pl", "ru", "uk", "tr", "ar", "he",
        "hi", "ja", "ko", "zh-CN", "zh-TW", "sv", "da", "fi", "nb", "cs", "el", "hu", "ro", "bg",
        "id", "vi", "th", "fa", "ca",
    )

    val sourceCodes = listOf(AUTO) + targetCodes

    fun defaultTarget(): String {
        val locale = Locale.getDefault()
        val withCountry = "${locale.language}-${locale.country}"
        return when {
            targetCodes.contains(withCountry) -> withCountry
            locale.language == "pt" -> "pt-BR"
            locale.language == "zh" -> "zh-CN"
            targetCodes.contains(locale.language) -> locale.language
            else -> "en"
        }
    }

    fun displayName(code: String, autoLabel: String, inLocale: Locale = Locale.getDefault()): String {
        if (code == AUTO) {
            return autoLabel
        }
        val locale = Locale.forLanguageTag(code)
        return locale.getDisplayName(inLocale).replaceFirstChar { it.titlecase(inLocale) }
    }

    /** English name, used inside LLM prompts. */
    fun englishName(code: String): String {
        if (code == AUTO) {
            return "the detected language"
        }
        return Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH)
    }
}
