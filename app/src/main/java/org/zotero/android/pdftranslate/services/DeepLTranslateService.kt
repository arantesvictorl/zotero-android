package org.zotero.android.pdftranslate.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.zotero.android.pdftranslate.data.TranslateError
import org.zotero.android.pdftranslate.data.TranslateLanguage
import org.zotero.android.pdftranslate.data.TranslateResult
import org.zotero.android.pdftranslate.data.TranslateServiceType
import org.zotero.android.pdftranslate.data.TranslateSettings

class DeepLTranslateService(private val client: OkHttpClient) : TranslateService {

    override fun translate(text: String, settings: TranslateSettings): TranslateResult {
        val apiKey = settings.deeplApiKey.trim()
        if (apiKey.isEmpty()) {
            throw TranslateError.MissingApiKey
        }
        // DeepL Free API keys end with ":fx".
        val host = if (apiKey.endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"

        val json = JsonObject().apply {
            add("text", JsonArray().apply { add(text) })
            addProperty("target_lang", mapTargetLanguage(settings.targetLanguage))
            mapSourceLanguage(settings.sourceLanguage)?.let { addProperty("source_lang", it) }
        }
        val request = Request.Builder()
            .url("https://$host/v2/translate")
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val body = client.executeForBody(request)
        val translation = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonArray("translations")
                .get(0).asJsonObject
        } catch (e: Exception) {
            throw TranslateError.EmptyResponse
        }
        val translated = translation.get("text")?.asString
        if (translated.isNullOrBlank()) {
            throw TranslateError.EmptyResponse
        }
        return TranslateResult(
            sourceText = text,
            translatedText = translated,
            service = TranslateServiceType.deepl,
            targetLanguage = settings.targetLanguage,
            detectedSourceLanguage = translation.get("detected_source_language")?.asString?.lowercase(),
        )
    }

    private fun mapTargetLanguage(code: String): String {
        return when (code) {
            "en" -> "EN-US"
            "pt-BR" -> "PT-BR"
            "pt-PT" -> "PT-PT"
            "zh-CN" -> "ZH-HANS"
            "zh-TW" -> "ZH-HANT"
            else -> code.substringBefore('-').uppercase()
        }
    }

    private fun mapSourceLanguage(code: String): String? {
        if (code == TranslateLanguage.AUTO) {
            return null
        }
        return code.substringBefore('-').uppercase()
    }
}
