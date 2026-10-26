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

/**
 * Talks to any server implementing the "chat/completions" endpoint, like the GPT service of zotero-pdf-translate.
 */
class OpenAiCompatibleTranslateService(private val client: OkHttpClient) : TranslateService {

    override fun translate(text: String, settings: TranslateSettings): TranslateResult {
        val baseUrl = settings.openAiBaseUrl.trim().trimEnd('/')
            .ifEmpty { TranslateSettings.DEFAULT_OPEN_AI_BASE_URL }
        val model = settings.openAiModel.trim().ifEmpty { TranslateSettings.DEFAULT_OPEN_AI_MODEL }
        val promptTemplate = settings.openAiPrompt.ifBlank { TranslateSettings.DEFAULT_OPEN_AI_PROMPT }

        val prompt = if (promptTemplate.contains(TranslateSettings.PLACEHOLDER_TEXT)) {
            promptTemplate
        } else {
            // Custom prompt without the text placeholder, append the text at the end.
            "$promptTemplate\n\n${TranslateSettings.PLACEHOLDER_TEXT}"
        }
            .replace(TranslateSettings.PLACEHOLDER_SOURCE_LANGUAGE, TranslateLanguage.englishName(settings.sourceLanguage))
            .replace(TranslateSettings.PLACEHOLDER_TARGET_LANGUAGE, TranslateLanguage.englishName(settings.targetLanguage))
            .replace(TranslateSettings.PLACEHOLDER_TEXT, text)

        val json = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            })
        }
        val requestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
        val apiKey = settings.openAiApiKey.trim()
        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val body = client.executeForBody(requestBuilder.build())
        val translated = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        } catch (e: Exception) {
            throw TranslateError.EmptyResponse
        }.trim().removePrefix("🔤").removeSuffix("🔤").trim()

        if (translated.isEmpty()) {
            throw TranslateError.EmptyResponse
        }
        return TranslateResult(
            sourceText = text,
            translatedText = translated,
            service = TranslateServiceType.openAiCompatible,
            targetLanguage = settings.targetLanguage,
        )
    }
}
