package org.zotero.android.pdftranslate

import android.util.LruCache
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.zotero.android.api.interceptors.HttpLoggingInterceptor
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.coroutines.Dispatchers
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateResult
import org.zotero.android.pdftranslate.data.TranslateServiceType
import org.zotero.android.pdftranslate.data.TranslateSettings
import org.zotero.android.pdftranslate.services.DeepLTranslateService
import org.zotero.android.pdftranslate.services.GoogleTranslateService
import org.zotero.android.pdftranslate.services.OpenAiCompatibleTranslateService
import org.zotero.android.pdftranslate.services.TranslateService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android port of the core of zotero-pdf-translate (https://github.com/windingwind/zotero-pdf-translate):
 * translates text selected in the reader using the service configured in settings.
 */
@Singleton
class PdfTranslator @Inject constructor(
    private val defaults: Defaults,
    private val dispatchers: Dispatchers,
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Never log bodies or headers, they contain API keys and the user's documents.
            .addInterceptor(HttpLoggingInterceptor.createInterceptor(Level.BASIC))
            .build()
    }

    private val services: Map<TranslateServiceType, TranslateService> by lazy {
        mapOf(
            TranslateServiceType.google to GoogleTranslateService(client),
            TranslateServiceType.deepl to DeepLTranslateService(client),
            TranslateServiceType.openAiCompatible to OpenAiCompatibleTranslateService(client),
        )
    }

    private val cache = LruCache<String, TranslateResult>(50)

    val settings: TranslateSettings
        get() = defaults.getTranslateSettings()

    suspend fun translate(
        text: String,
        settings: TranslateSettings = this.settings,
    ): TranslateResult {
        val sourceText = if (settings.cleanUpSourceText) cleanUp(text) else text.trim()
        if (sourceText.isEmpty()) {
            throw TranslateError.EmptyText
        }
        val cacheKey = cacheKey(sourceText, settings)
        cache.get(cacheKey)?.let { return it }

        val service = services.getValue(settings.service)
        val result = withContext(dispatchers.io) {
            service.translate(sourceText, settings)
        }
        cache.put(cacheKey, result)
        return result
    }

    private fun cacheKey(text: String, settings: TranslateSettings): String {
        val modelPart = if (settings.service == TranslateServiceType.openAiCompatible) {
            "${settings.openAiBaseUrl}|${settings.openAiModel}|${settings.openAiPrompt.hashCode()}"
        } else {
            ""
        }
        return "${settings.service}|${settings.sourceLanguage}|${settings.targetLanguage}|$modelPart|$text"
    }

    companion object {
        /**
         * Text selected in PDFs keeps the line breaks of the page layout and words split by hyphenation,
         * which degrades translation quality.
         */
        fun cleanUp(text: String): String {
            return text
                .replace("\r\n", "\n")
                // "transla-\ntion" -> "translation"
                .replace(Regex("(\\p{L})-\\n(\\p{Ll})"), "$1$2")
                // Keep paragraph breaks, join the other lines.
                .replace(Regex("\\n{2,}"), "\u0000")
                .replace('\n', ' ')
                .replace('\u0000', '\n')
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
        }
    }
}
