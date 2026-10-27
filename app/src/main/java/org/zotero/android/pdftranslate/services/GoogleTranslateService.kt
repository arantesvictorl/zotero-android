package org.zotero.android.pdftranslate.services

import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateLanguage
import org.zotero.android.pdftranslate.data.TranslateResult
import org.zotero.android.pdftranslate.data.TranslateServiceType
import org.zotero.android.pdftranslate.data.TranslateSettings

/**
 * Uses the free "gtx" endpoint of Google Translate, same as the "Google (API)" service of zotero-pdf-translate.
 */
class GoogleTranslateService(private val client: OkHttpClient) : TranslateService {

    override fun translate(text: String, settings: TranslateSettings): TranslateResult {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", mapLanguage(settings.sourceLanguage))
            .addQueryParameter("tl", mapLanguage(settings.targetLanguage))
            .addQueryParameter("dt", "t")
            .addQueryParameter("dj", "0")
            .build()
        // POST so that long selections don't exceed the URL length limit.
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().add("q", text).build())
            .build()

        val body = client.executeForBody(request)
        val root = try {
            JsonParser.parseString(body).asJsonArray
        } catch (e: Exception) {
            throw TranslateError.EmptyResponse
        }
        val segments = root.get(0)?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw TranslateError.EmptyResponse
        val translated = buildString {
            for (segment in segments) {
                val part = segment.takeIf { it.isJsonArray }?.asJsonArray?.get(0)
                if (part != null && part.isJsonPrimitive) {
                    append(part.asString)
                }
            }
        }
        if (translated.isBlank()) {
            throw TranslateError.EmptyResponse
        }
        val detected = root.takeIf { it.size() > 2 }?.get(2)
            ?.takeIf { it.isJsonPrimitive }?.asString

        return TranslateResult(
            sourceText = text,
            translatedText = translated,
            service = TranslateServiceType.google,
            targetLanguage = settings.targetLanguage,
            detectedSourceLanguage = detected,
        )
    }

    private fun mapLanguage(code: String): String {
        return when (code) {
            TranslateLanguage.AUTO -> "auto"
            "pt-BR" -> "pt"
            "nb" -> "no"
            "he" -> "iw"
            else -> code
        }
    }
}
