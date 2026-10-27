package org.zotero.android.pdftranslate.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.zotero.android.pdftranslate.data.LlmAction
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
        val promptTemplate = settings.openAiPrompt.ifBlank { TranslateSettings.DEFAULT_OPEN_AI_PROMPT }
        val prompt = fillTemplate(
            template = withTextPlaceholder(promptTemplate),
            values = mapOf(
                TranslateSettings.PLACEHOLDER_SOURCE_LANGUAGE to TranslateLanguage.englishName(settings.sourceLanguage),
                TranslateSettings.PLACEHOLDER_TARGET_LANGUAGE to TranslateLanguage.englishName(settings.targetLanguage),
                TranslateSettings.PLACEHOLDER_TEXT to text,
            )
        )
        val translated = complete(prompt = prompt, settings = settings)
            .removePrefix("🔤").removeSuffix("🔤").trim()
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

    /**
     * Runs a user defined [action] on [text], using the surrounding text of the document as context.
     */
    fun runAction(
        action: LlmAction,
        text: String,
        contextBefore: String,
        contextAfter: String,
        title: String,
        settings: TranslateSettings,
    ): String {
        val prompt = fillTemplate(
            template = withTextPlaceholder(action.prompt.ifBlank { LlmAction.NEW_ACTION_PROMPT }),
            values = mapOf(
                TranslateSettings.PLACEHOLDER_SOURCE_LANGUAGE to TranslateLanguage.englishName(settings.sourceLanguage),
                TranslateSettings.PLACEHOLDER_TARGET_LANGUAGE to TranslateLanguage.englishName(settings.targetLanguage),
                TranslateSettings.PLACEHOLDER_TITLE to title.ifBlank { "unknown" },
                TranslateSettings.PLACEHOLDER_CONTEXT_BEFORE to contextBefore,
                TranslateSettings.PLACEHOLDER_CONTEXT_AFTER to contextAfter,
                TranslateSettings.PLACEHOLDER_TEXT to text,
            )
        )
        return complete(prompt = prompt, settings = settings)
    }

    private fun complete(prompt: String, settings: TranslateSettings): String {
        val baseUrl = settings.openAiBaseUrl.trim().trimEnd('/')
            .ifEmpty { TranslateSettings.DEFAULT_OPEN_AI_BASE_URL }
        val model = settings.openAiModel.trim().ifEmpty { TranslateSettings.DEFAULT_OPEN_AI_MODEL }

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
        val content = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        } catch (e: Exception) {
            throw TranslateError.EmptyResponse
        }.trim()
        if (content.isEmpty()) {
            throw TranslateError.EmptyResponse
        }
        return content
    }

    private fun withTextPlaceholder(template: String): String {
        return if (template.contains(TranslateSettings.PLACEHOLDER_TEXT)) {
            template
        } else {
            // Custom prompt without the text placeholder, append the text at the end.
            "$template\n\n${TranslateSettings.PLACEHOLDER_TEXT}"
        }
    }

    companion object {
        private val placeholderRegex = Regex("\\{[A-Za-z]+\\}")

        /**
         * Replaces all placeholders in a single pass, so placeholders that happen to appear in the
         * document text itself are left untouched.
         */
        fun fillTemplate(template: String, values: Map<String, String>): String {
            return placeholderRegex.replace(template) { match ->
                values[match.value] ?: match.value
            }
        }
    }
}
